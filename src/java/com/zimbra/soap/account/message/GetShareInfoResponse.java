/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013 Zimbra Software, LLC.
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.4 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap.account.message;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.zimbra.common.soap.AccountConstants;
import com.zimbra.soap.type.ShareInfo;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name=AccountConstants.E_GET_SHARE_INFO_RESPONSE)
public class GetShareInfoResponse {

    /**
     * @zm-api-field-description Shares
     */
    @XmlElement(name=AccountConstants.E_SHARE, required=false)
    private List<ShareInfo> shares = Lists.newArrayList();

    public GetShareInfoResponse() {
    }

    public void setShares(Iterable <ShareInfo> shares) {
        this.shares.clear();
        if (shares != null) {
            Iterables.addAll(this.shares,shares);
        }
    }

    public GetShareInfoResponse addShar(ShareInfo shar) {
        this.shares.add(shar);
        return this;
    }

    public List<ShareInfo> getShares() {
        return Collections.unmodifiableList(shares);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
            .add("shares", shares)
            .toString();
    }
}
