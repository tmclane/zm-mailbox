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

package com.zimbra.soap.type;

import java.util.Arrays;
import javax.xml.bind.annotation.XmlEnum;

import com.zimbra.common.service.ServiceException;

@XmlEnum
public enum AccountBy {
    // case must match protocol
    adminName, appAdminName, id, foreignPrincipal, name, krb5Principal;

    public static AccountBy fromString(String s)
    throws ServiceException {
        try {
            return AccountBy.valueOf(s);
        } catch (IllegalArgumentException e) {
           throw ServiceException.INVALID_REQUEST("unknown 'By' key: " + s + ", valid values: " +
                   Arrays.asList(AccountBy.values()), null);
        }
    }

    public com.zimbra.common.account.Key.AccountBy toKeyDomainBy()
    throws ServiceException {
        return com.zimbra.common.account.Key.AccountBy.fromString(this.name());
    }
}
