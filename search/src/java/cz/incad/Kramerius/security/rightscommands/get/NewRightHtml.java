/*
 * Copyright (C) 2010 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.Kramerius.security.rightscommands.get;

import static cz.incad.utils.IKeys.UUID_PARAMETER;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Level;

import org.antlr.stringtemplate.StringTemplate;

import com.google.inject.Inject;

import cz.incad.Kramerius.security.ServletCommand;
import cz.incad.Kramerius.security.rightscommands.ServletRightsCommand;
import cz.incad.Kramerius.security.strenderers.CriteriumWrapper;
import cz.incad.Kramerius.security.strenderers.SecuredActionWrapper;
import cz.incad.Kramerius.security.strenderers.TitlesForObjects;
import cz.incad.kramerius.security.RightCriterium;
import cz.incad.kramerius.security.RightCriteriumLoader;
import cz.incad.kramerius.security.RightCriteriumParams;
import cz.incad.kramerius.security.SecuredActions;
import cz.incad.kramerius.security.impl.criteria.CriteriumsLoader;

/**
 * Formular pro nove pravo
 * @author pavels
 */
public class NewRightHtml extends ServletRightsCommand {

    static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(NewRightHtml.class.getName());
    
    @Inject
    RightCriteriumLoader criteriumLoader;

    
    
    @Override
    public void doCommand() {
        
        try {
            String uuid = getUuid();
            String[] path = getPathOfUUIDs(uuid);
            String[] models = getModels(uuid);
            ResourceBundle resourceBundle = getResourceBundle();

            StringTemplate template = ServletRightsCommand.stFormsGroup().getInstanceOf("rightDialog");
            HashMap<String, String> titles = TitlesForObjects.createFinerTitles(fedoraAccess,rightsManager, uuid, path, models, resourceBundle);

            List<String> saturatedPath = rightsManager.saturatePathAndCreatesPIDs(uuid, path);
            
            RightCriteriumParams[] allParams = rightsManager.findAllParams();
            template.setAttribute("allParams", allParams);
            template.setAttribute("titles", titles);
            template.setAttribute("uuid", uuid);
            Map<String, String> bundleToMap = bundleToMap(); {
                bundleToMap.put("rights.dialog.rightassociationtitle", MessageFormat.format(bundleToMap.get("rights.dialog.rightassociationtitle"), SecuredActions.findByFormalName(getSecuredAction())));
            }
            
            template.setAttribute("bundle", bundleToMap);

            template.setAttribute("action", new SecuredActionWrapper(resourceBundle, SecuredActions.findByFormalName(getSecuredAction())));
            template.setAttribute("objects", saturatedPath);
            //template.setAttribute("criteriumNames",CriteriumWrapper.wrapCriteriums(CriteriumsLoader.criteriums(), true));
            List<RightCriterium> criteriums = criteriumLoader.getCriteriums(SecuredActions.findByFormalName(getSecuredAction()));
            template.setAttribute("allCriteriums",CriteriumWrapper.wrapCriteriums(criteriums, true));
            String content = template.toString();
            this.responseProvider.get().getOutputStream().write(content.getBytes("UTF-8"));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
        
    }

    
}
