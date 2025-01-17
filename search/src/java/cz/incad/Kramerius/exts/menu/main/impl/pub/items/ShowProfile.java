/*
 * Copyright (C) 2012 Pavel Stastny
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
package cz.incad.Kramerius.exts.menu.main.impl.pub.items;

import java.io.IOException;

import cz.incad.Kramerius.exts.menu.main.impl.AbstractMainMenuItem;
import cz.incad.Kramerius.exts.menu.main.impl.pub.PublicMainMenuItem;
import cz.incad.kramerius.auth.thirdparty.shibb.utils.ShibbolethUtils;

public class ShowProfile extends AbstractMainMenuItem implements PublicMainMenuItem {

    @Override
    public boolean isRenderable() {
        return (!ShibbolethUtils.isUnderShibbolethSession(this.requestProvider.get()));
    }


    @Override
    public String getRenderedItem() throws IOException {
        return renderMainMenuItem(
                "javascript:showSearchHistory.showHistory(); javascript:hideAdminMenu();",
                "administrator.menu.dialogs.profile.title", false);
     }

}
