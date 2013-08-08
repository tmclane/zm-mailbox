/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013 Zimbra Software, LLC.
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap.admin.message;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.zimbra.common.soap.AdminConstants;
import com.zimbra.soap.admin.type.EffectiveAttrsInfo;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name=AdminConstants.E_GET_CREATE_OBJECT_ATTRS_RESPONSE)
public class GetCreateObjectAttrsResponse {

    /**
     * @zm-api-field-description Set attributes
     */
    @XmlElement(name=AdminConstants.E_SET_ATTRS, required=true)
    private final EffectiveAttrsInfo setAttrs;

    /**
     * no-argument constructor wanted by JAXB
     */
    @SuppressWarnings("unused")
    private GetCreateObjectAttrsResponse() {
        this((EffectiveAttrsInfo) null);
    }

    public GetCreateObjectAttrsResponse(EffectiveAttrsInfo setAttrs) {
        this.setAttrs = setAttrs;
    }

    public EffectiveAttrsInfo getSetAttrs() { return setAttrs; }
}
