//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.gui.swing.jdgui.menu.actions.sendlogs;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.appwork.exceptions.WTFException;
import org.appwork.utils.IO;
import org.appwork.utils.logging2.sendlogs.AbstractLogAction;
import org.appwork.utils.logging2.sendlogs.LogFolder;
import org.appwork.utils.swing.dialog.Dialog;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.NewTheme;
import org.jdownloader.jdserv.JD_SERV_CONSTANTS;
import org.jdownloader.jdserv.UploadInterface;
import org.jdownloader.logging.LogController;

public class LogAction extends AbstractLogAction {

    protected String id;

    public LogAction() {
        super();
        setName(_GUI._.LogAction());
        setSmallIcon(NewTheme.I().getIcon("log", 22));
        setTooltipText(_GUI._.LogAction_tooltip());
        id = null;
    }

    @Override
    protected void createPackage(List<LogFolder> selection) throws Exception {
        id = null;
        super.createPackage(selection);
        if (id != null) {
            String name = format(selection.get(0).getCreated()) + "to" + format(selection.get(selection.size() - 1).getLastModified());
            Dialog.getInstance().showInputDialog(0, _GUI._.LogAction_actionPerformed_givelogid_(), name + " jdlog://" + id + "/");
        }
    }

    @Override
    protected void onNewPackage(File zip, String name) throws IOException {
        try {
            if (Thread.currentThread().isInterrupted()) throw new WTFException("INterrupted");
            id = JD_SERV_CONSTANTS.CLIENT.create(UploadInterface.class).upload(IO.readFile(zip), "", id);
            if (Thread.currentThread().isInterrupted()) throw new WTFException("INterrupted");

        } catch (Exception e) {
            Dialog.getInstance().showExceptionDialog("Exception ocurred", e.getMessage(), e);
        }
    }

    @Override
    protected void flushLogs() {
        LogController.getInstance().flushSinks(true, false);
    }

    @Override
    protected boolean isCurrentLogFolder(long timestamp) {
        long startup = LogController.getInstance().getInitTime();
        return startup == timestamp;
    }

}
