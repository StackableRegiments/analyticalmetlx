/**
 * apache2
 */
package com.metlingpot.model;

/**
 * 
 */
public class InternalServerErrorException extends com.metlingpot.model.MetlingPotInputItemException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new InternalServerErrorException with the specified error message.
     *
     * @param message
     *        Describes the error encountered.
     */
    public InternalServerErrorException(String message) {
        super(message);
    }

}
