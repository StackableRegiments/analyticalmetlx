/**
 * apache2
 */
package com.metlingpot;

import com.metlingpot.model.*;
import com.amazonaws.*;
import com.amazonaws.opensdk.*;
import com.amazonaws.opensdk.model.*;

/**
 * Abstract implementation of {@code MetlingPotInputItem}.
 */
public class AbstractMetlingPotInputItem implements MetlingPotInputItem {

    protected AbstractMetlingPotInputItem() {
    }

    @Override
    public PutInputItemResult putInputItem(PutInputItemRequest request) {
        throw new java.lang.UnsupportedOperationException();
    }

    @Override
    public PutSearchResult putSearch(PutSearchRequest request) {
        throw new java.lang.UnsupportedOperationException();
    }

    @Override
    public PutSmartgroupsResult putSmartgroups(PutSmartgroupsRequest request) {
        throw new java.lang.UnsupportedOperationException();
    }

    @Override
    public RawResult execute(RawRequest request) {
        throw new java.lang.UnsupportedOperationException();
    }

    @Override
    public RawResult execute(RawRequest request, ResultContentConsumer consumer) {
        throw new java.lang.UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new java.lang.UnsupportedOperationException();
    }

}
