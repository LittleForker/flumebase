// (c) Copyright 2010 Odiago, Inc.

package com.odiago.rtengine.io;

import java.nio.CharBuffer;

import java.util.ArrayList;

import org.apache.avro.util.Utf8;

import com.cloudera.flume.core.Event;

import com.odiago.rtengine.lang.NullableType;
import com.odiago.rtengine.lang.Type;

/**
 * EventParser implementation that uses a delimiter character in between fields.
 * The delimiter character cannot appear in the fields themselves;
 * this does not support any enclosed- or escaped-by characters.
 */
public class DelimitedEventParser extends EventParser {

  /** The event we're processing. */
  private Event mEvent;

  /** A UTF-8 wrapper around the event's bytes. */
  private Utf8 mAsUtf8;

  /** A char array representation converted from the event's bytes. */
  private char [] mAsCharacters;

  /** The current cursor index into mAsCharacters. */
  private int mIndex;

  /** The delimiter character we're using. */
  private char mDelimiter;

  /** Index of the field we will walk across next. */ 
  private int mCurField;

  /** CharBuffers wrapping the text of each column. */
  private ArrayList<CharBuffer> mColTexts;

  /** The reified instances of the columns in their final types. A null
   * here may mean 'uncached', or true null, if mColumnNulls[i] is true. */
  private ArrayList<Object> mColumnValues;

  /** Array of t/f values; if true, indicates that we parsed the column
   * in question, but determined the value to be null. */
  private ArrayList<Boolean> mColumnNulls;
  
  public static final char DEFAULT_DELIMITER = ',';

  public DelimitedEventParser() {
    this(DEFAULT_DELIMITER);
  }

  public DelimitedEventParser(char delimiter) {
    mDelimiter = delimiter;
    mColTexts = new ArrayList<CharBuffer>();
    mColumnValues = new ArrayList<Object>();
    mColumnNulls = new ArrayList<Boolean>();
  }

  /** Clear all internal state and reset to a new unparsed event body. */
  @Override
  public void reset(Event e) {
    mEvent = e;
    mAsUtf8 = new Utf8(mEvent.getBody());
    mAsCharacters = null;
    mIndex = 0;
    mColTexts.clear();
    mColumnValues.clear();
    mColumnNulls.clear();
  }

  /**
   * Return the value of the colIdx'th column in the expected type form.
   *
   * <p>
   * First, check if we've already cached the value. If so, return it.
   * Next, check if we've cached a CharBuffer that wraps the underlying text.
   * If so, convert that to the correct value, cache it, and return it.
   * Finally, walk forward from our current position in mAsCharacters,
   * looking for delimiters. As we find delimiters, mark and cache the
   * discovered columns in mColTexts. When we arrive at the column of
   * interest, cache and return its value.
   * </p>
   */
  @Override
  public Object getColumn(int colIdx, Type expectedType) throws ColumnParseException {
    // Check if we've cached a value for the column.
    if (mColumnValues.size() > colIdx) {
      // We may have cached a value for this column.
      Object cached = mColumnValues.get(colIdx);
      if (cached != null) {
        // Already parsed - return it!
        return cached;
      }

      // We got null from the cache; this may mean not-yet-parsed, or it
      // might be true null.
      if (mColumnNulls.get(colIdx)) {
        return null; // True null.
      }
    }

    // Check if we've cached a wrapper for the column bytes.
    CharBuffer cbCol = mColTexts.size() > colIdx ? mColTexts.get(colIdx) : null;

    if (null != cbCol) {
      // We have. Interpret the bytes inside.
      return parseAndCache(cbCol, colIdx, expectedType);
    }

    // Check if we have yet decoded the UTF-8 bytes of the event into a char
    // array.
    if (mAsCharacters == null) {
      // Nope, do so now.
      // TODO(aaron): Make sure we're not making an additional copy. The
      // toString() call converts the bytes into a String; Does String.toCharArray()
      // then make an additional copy of the backing array? Can we do better?
      mAsCharacters = mAsUtf8.toString().toCharArray();
    }

    // While we have to walk more fields to get the one we need...
    CharBuffer cbField = null;
    while (mCurField <= colIdx) {
      // We have to continue walking through the underlying string.
      int start = mIndex; // The field starts here.
      if (start >= mAsCharacters.length) {
        // We don't have any more fields we can parse. If we need to read
        // more fields, then this is an error; the event is too short.
        throw new ColumnParseException("Not enough fields");
      }

      for ( ; mIndex < mAsCharacters.length; mIndex++) {
        char cur = mAsCharacters[mIndex];
        if (mDelimiter == cur) {
          // Found the end of the current field.
          break;
        }
      }

      // We have ended the current field, either by finding its delimiter, or hitting
      // the end of the entire record. Wrap this field in a character buffer, and
      // memoize it.
      int delimPos = mIndex;
      cbField = CharBuffer.wrap(mAsCharacters, start, delimPos - start);

      mColTexts.add(cbField); // Always add fields to the end of the list.
      mCurField++; // We've added another field.
    }

    // We have separated enough fields; this one's text is cached. Parse its
    // value and return it.
    return parseAndCache(cbField, colIdx, expectedType);
  }

  /**
   * Given a CharBuffer wrapping a field, return the field's value in the
   * type expected by the runtime. Before returning, cache it in the slot for
   * 'colIdx'.
   */
  private Object parseAndCache(CharBuffer chars, int colIdx, Type expectedType)
      throws ColumnParseException {
    Type.TypeName primitiveTypeName = expectedType.getTypeName();

    if (expectedType.isNullable()) {
      // TODO: Should nullables store the nullable type name as the base so that
      // getTypeName() returns the "right thing" here?
      primitiveTypeName = ((NullableType) expectedType).getNullableTypeName();
    }

    // TODO(aaron): Handle null values (basically, empty strings.. except for the
    // STRING type?).
    Object out = null;
    switch (primitiveTypeName) {
    case BOOLEAN:
      out = CharBufferUtils.parseBool(chars);
      break;
    case INT:
      out = CharBufferUtils.parseInt(chars);
      break;
    case BIGINT:
      out = CharBufferUtils.parseLong(chars);
      break;
    case FLOAT:
      out = CharBufferUtils.parseFloat(chars);
      break;
    case DOUBLE:
      out = CharBufferUtils.parseDouble(chars);
      break;
    case STRING:
      out = CharBufferUtils.parseString(chars);
      break;
    case TIMESTAMP:
      out = CharBufferUtils.parseLong(chars);
      if (null != out) {
        out = new java.sql.Timestamp((Long) out);
      }
      break;
    case TIMESPAN:
      out = CharBufferUtils.parseLong(chars);
      break;
    default:
      throw new ColumnParseException("Cannot parse recursive types");
    }

    while(mColumnValues.size() < colIdx) {
      // Add nulls to the list to increase the memoized size up to this column.
      mColumnValues.add(null);

      // Add a 'false' in the mColumnNulls so we know these are "padding" nulls
      // and not "true" nulls.
      mColumnNulls.add(Boolean.valueOf(false));
    }

    // Now add this parsed value to the end of the list. Sets its null bit appropriately.
    mColumnValues.add(out);
    mColumnValues.add(Boolean.valueOf(out == null));

    return out;
  }

  @Override
  public String toString() {
    return "DelimitedEventParser(delimiter=" + mDelimiter + ")";
  }

}