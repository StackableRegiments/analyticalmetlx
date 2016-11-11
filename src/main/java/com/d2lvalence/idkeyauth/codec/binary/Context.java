package com.d2lvalence.idkeyauth.codec.binary;

import java.util.Arrays;

/**
 * Holds thread context so classes can be thread-safe.
 *
 * This class is not itself thread-safe; each thread must allocate its own copy.
 *
 * @since 1.7
 */
class Context {

    /**
     * Place holder for the bytes we're dealing with for our based logic.
     * Bitwise operations store and extract the encoding or decoding from this variable.
     */
    int ibitWorkArea;

    /**
     * Place holder for the bytes we're dealing with for our based logic.
     * Bitwise operations store and extract the encoding or decoding from this variable.
     */
    long lbitWorkArea;

    /**
     * Buffer for streaming.
     */
    byte[] buffer;

    /**
     * Position where next character should be written in the buffer.
     */
    int pos;

    /**
     * Position where next character should be read from the buffer.
     */
    int readPos;

    /**
     * Boolean flag to indicate the EOF has been reached. Once EOF has been reached, this object becomes useless,
     * and must be thrown away.
     */
    boolean eof;

    /**
     * Variable tracks how many characters have been written to the current line. Only used when encoding. We use
     * it to make sure each encoded line never goes beyond lineLength (if lineLength > 0).
     */
    int currentLinePos;

    /**
     * Writes to the buffer only occur after every 3/5 reads when encoding, and every 4/8 reads when decoding. This
     * variable helps track that.
     */
    int modulus;

    Context() {
    }

    /**
     * Returns a String useful for debugging (especially within a debugger.)
     *
     * @return a String useful for debugging.
     */
    @SuppressWarnings("boxing") // OK to ignore boxing here
    @Override
    public String toString() {
        return String.format("%s[buffer=%s, currentLinePos=%s, eof=%s, ibitWorkArea=%s, lbitWorkArea=%s, " +
                        "modulus=%s, pos=%s, readPos=%s]", this.getClass().getSimpleName(), Arrays.toString(buffer),
                currentLinePos, eof, ibitWorkArea, lbitWorkArea, modulus, pos, readPos);
    }
}