/**
 * apache2
 */
package com.metlingpot.model;

import com.amazonaws.opensdk.SdkErrorHttpMetadata;
import com.amazonaws.opensdk.internal.BaseException;
import com.amazonaws.annotation.SdkInternalApi;

/**
 * Base exception for all service exceptions thrown by MetlingPotInputItem
 */
public class MetlingPotInputItemException extends com.amazonaws.SdkBaseException implements BaseException {

    private static final long serialVersionUID = 1L;

    private SdkErrorHttpMetadata sdkHttpMetadata;

    private String message;

    /**
     * Constructs a new MetlingPotInputItemException with the specified error message.
     *
     * @param message
     *        Describes the error encountered.
     */
    public MetlingPotInputItemException(String message) {
        super(message);
        this.message = message;
    }

    @Override
    public MetlingPotInputItemException sdkHttpMetadata(SdkErrorHttpMetadata sdkHttpMetadata) {
        this.sdkHttpMetadata = sdkHttpMetadata;
        return this;
    }

    @Override
    public SdkErrorHttpMetadata sdkHttpMetadata() {
        return sdkHttpMetadata;
    }

    @SdkInternalApi
    @Override
    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
