/**
 * apache2
 */
package com.metlingpot;

import com.amazonaws.annotation.NotThreadSafe;
import com.amazonaws.client.AwsSyncClientParams;
import com.amazonaws.opensdk.protect.client.SdkSyncClientBuilder;
import com.amazonaws.opensdk.internal.config.ApiGatewayClientConfigurationFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.Signer;
import com.amazonaws.util.RuntimeHttpUtils;
import com.amazonaws.Protocol;

import java.net.URI;

/**
 * Fluent builder for {@link com.metlingpot.MetlingPotInputItem}. Use of the builder is preferred over using
 * constructors of the client class.
 **/
@NotThreadSafe
public final class MetlingPotInputItemClientBuilder extends SdkSyncClientBuilder<MetlingPotInputItemClientBuilder, MetlingPotInputItem> {

    private static final URI DEFAULT_ENDPOINT = RuntimeHttpUtils.toUri("jq6f5gsrq9.execute-api.us-east-1.amazonaws.com", Protocol.HTTPS);
    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * Package private constructor - builder should be created via {@link MetlingPotInputItem#builder()}
     */
    MetlingPotInputItemClientBuilder() {
        super(new ApiGatewayClientConfigurationFactory());
    }

    /**
     * Specify the API Key to send with requests.
     *
     * @param apiKey
     *        API Key that identifies caller.
     * @return This object for method chaining.
     */
    @Override
    public void setApiKey(String apiKey) {
        super.setApiKey(apiKey);
    }

    /**
     * Specify the API Key to send with requests.
     *
     * @param apiKey
     *        API Key that identifies caller.
     * @return This object for method chaining.
     */
    public MetlingPotInputItemClientBuilder apiKey(String apiKey) {
        setApiKey(apiKey);
        return this;
    }

    /**
     * Specify an implementation of {@link AWSCredentialsProvider} to be used when signing IAM auth'd requests
     *
     * @param iamCredentials
     *        the credential provider
     */
    @Override
    public void setIamCredentials(AWSCredentialsProvider iamCredentials) {
        super.setIamCredentials(iamCredentials);
    }

    /**
     * Specify an implementation of {@link AWSCredentialsProvider} to be used when signing IAM auth'd requests
     *
     * @param iamCredentials
     *        the credential provider
     * @return This object for method chaining.
     */
    public MetlingPotInputItemClientBuilder iamCredentials(AWSCredentialsProvider iamCredentials) {
        setIamCredentials(iamCredentials);
        return this;
    }

    /**
     * Sets the IAM region to use when using IAM auth'd requests against a service in any of it's non-default regions.
     * This is only expected to be used when a custom endpoint has also been set.
     *
     * @param iamRegion
     *        the IAM region string to use when signing
     */
    public void setIamRegion(String iamRegion) {
        super.setIamRegion(iamRegion);
    }

    /**
     * Sets the IAM region to use when using IAM auth'd requests against a service in any of it's non-default regions.
     * This is only expected to be used when a custom endpoint has also been set.
     *
     * @param iamRegion
     *        the IAM region string to use when signing
     * @return This object for method chaining.
     */
    public MetlingPotInputItemClientBuilder iamRegion(String iamRegion) {
        setIamRegion(iamRegion);
        return this;
    }

    /**
     * Construct a synchronous implementation of MetlingPotInputItem using the current builder configuration.
     *
     * @param params
     *        Current builder configuration represented as a parameter object.
     * @return Fully configured implementation of MetlingPotInputItem.
     */
    @Override
    protected MetlingPotInputItem build(AwsSyncClientParams params) {
        return new MetlingPotInputItemClient(params);
    }

    @Override
    protected URI defaultEndpoint() {
        return DEFAULT_ENDPOINT;
    }

    @Override
    protected String defaultRegion() {
        return DEFAULT_REGION;
    }

    @Override
    protected Signer defaultIamSigner() {
        return signerFactory().createSigner();
    }
}
