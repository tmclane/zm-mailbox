package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.GranteeBy;
import com.zimbra.cs.account.Provisioning.TargetBy;
import com.zimbra.cs.account.accesscontrol.GranteeType;
import com.zimbra.cs.account.accesscontrol.RightCommand;
import com.zimbra.cs.account.accesscontrol.TargetType;
import com.zimbra.soap.ZimbraSoapContext;

public class GetEffectiveRights  extends RightDocumentHandler {
    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        
        Element eTarget = request.getElement(AdminConstants.E_TARGET);
        String targetType = eTarget.getAttribute(AdminConstants.A_TYPE);
        TargetBy targetBy = null;
        String target = null;
        if (TargetType.fromString(targetType).needsTargetIdentity()) {
            targetBy = TargetBy.fromString(eTarget.getAttribute(AdminConstants.A_BY));
            target = eTarget.getText();
        }
            
        Element eGrantee = request.getOptionalElement(AdminConstants.E_GRANTEE);
        GranteeBy granteeBy;
        String grantee;
        if (eGrantee != null) {
            String granteeType = eGrantee.getAttribute(AdminConstants.A_TYPE, GranteeType.GT_USER.getCode());
            if (GranteeType.fromCode(granteeType) != GranteeType.GT_USER)
                throw ServiceException.INVALID_REQUEST("invalid grantee type " + granteeType, null);
            granteeBy = GranteeBy.fromString(eGrantee.getAttribute(AdminConstants.A_BY));
            grantee = eGrantee.getText();
        } else {
            granteeBy = GranteeBy.id;
            grantee = zsc.getRequestedAccountId();  // TODO: need to check if the authe user has right to do this the request account, if they are not the same
        }
        
        RightCommand.EffectiveRights er = RightCommand.getEffectiveRights(Provisioning.getInstance(),
                                                         targetType, targetBy, target,
                                                         granteeBy, grantee);
        
        Element resp = zsc.createElement(AdminConstants.GET_EFFECTIVE_RIGHTS_RESPONSE);
        er.toXML(resp);
        return resp;
    }

}
