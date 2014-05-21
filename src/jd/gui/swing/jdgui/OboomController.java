package jd.gui.swing.jdgui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;

import jd.SecondLevelLaunch;
import jd.controlling.AccountController;
import jd.controlling.AccountControllerEvent;
import jd.controlling.AccountControllerListener;
import jd.gui.swing.jdgui.oboom.OboomDialog;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;

import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.txtresource.TranslationFactory;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.IO;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.swing.EDTRunner;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.dialog.DialogNoAnswerException;
import org.jdownloader.gui.translate._GUI;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;
import org.jdownloader.logging.LogController;
import org.jdownloader.settings.staticreferences.CFG_GUI;

public class OboomController implements TopRightPainter, AccountControllerListener {

    private MainTabbedPane pane;
    protected boolean      visible;
    private boolean        enabled         = false;
    private boolean        mouseover;
    private AbstractIcon   icon;
    private LogSource      logger;
    private AbstractIcon   close;
    private Rectangle      closeBounds;
    private boolean        getProMode      = false;
    private AbstractIcon   getproIcon;
    public static boolean  OFFER_IS_ACTIVE = OboomController.readOfferActive();

    public OboomController(MainTabbedPane panel) {
        logger = LogController.getInstance().getLogger("OboomDeal");
        this.pane = panel;
        close = new AbstractIcon("close", -1);
        String key = "oboom/jdbanner_free_" + TranslationFactory.getDesiredLocale().getLanguage().toLowerCase(Locale.ENGLISH);
        if (!NewTheme.I().hasIcon(key)) {
            key = "oboom/jdbanner_free_en";
        }
        icon = new AbstractIcon(key, -1);

        key = "oboom/jdbanner_getpro_" + TranslationFactory.getDesiredLocale().getLanguage().toLowerCase(Locale.ENGLISH);
        if (!NewTheme.I().hasIcon(key)) {
            key = "oboom/jdbanner_getpro_en";
        }
        getproIcon = new AbstractIcon(key, -1);
        CFG_GUI.SPECIAL_DEALS_ENABLED.getEventSender().addListener(new GenericConfigEventListener<Boolean>() {

            @Override
            public void onConfigValueModified(KeyHandler<Boolean> keyHandler, Boolean newValue) {
                enabled = newValue;
                new EDTRunner() {

                    @Override
                    protected void runInEDT() {
                        pane.repaint();
                    }
                };
            }

            @Override
            public void onConfigValidatorError(KeyHandler<Boolean> keyHandler, Boolean invalidValue, ValidationException validateException) {
            }
        });
        enabled = CFG_GUI.SPECIAL_DEALS_ENABLED.getValue();
        SecondLevelLaunch.ACCOUNTLIST_LOADED.executeWhenReached(new Runnable() {

            @Override
            public void run() {
                AccountController.getInstance().getBroadcaster().addListener(OboomController.this, true);
                onAccountControllerEvent(null);
            }
        });

    }

    @Override
    public Rectangle paint(Graphics2D g) {

        if (isVisible()) {
            if (getProMode) {
                Icon icon = getproIcon;
                icon.paintIcon(pane, g, pane.getWidth() - icon.getIconWidth() - 2, 22 - icon.getIconHeight());
                Rectangle specialDealBounds = new Rectangle(pane.getWidth() - icon.getIconWidth() - 2, 22 - icon.getIconHeight(), icon.getIconWidth(), icon.getIconHeight() + 2);

                if (mouseover) {
                    g.setColor(Color.GRAY);
                    g.drawLine(specialDealBounds.x, specialDealBounds.y + specialDealBounds.height - 1, specialDealBounds.x + specialDealBounds.width - 2, specialDealBounds.y + specialDealBounds.height - 1);
                    g.setColor(Color.WHITE);
                    g.fillRect(pane.getWidth() - close.getIconWidth() - 3, 22 - icon.getIconHeight() - 2, 9, 11);
                    closeBounds = new Rectangle(pane.getWidth() - close.getIconWidth() - 3, 22 - icon.getIconHeight() - 2, 9, 11);
                    close.paintIcon(pane, g, pane.getWidth() - close.getIconWidth() - 2, 22 - icon.getIconHeight());
                } else {

                }
                return specialDealBounds;
            } else {
                icon.paintIcon(pane, g, pane.getWidth() - icon.getIconWidth() - 2, 22 - icon.getIconHeight());
                Rectangle specialDealBounds = new Rectangle(pane.getWidth() - icon.getIconWidth() - 2, 22 - icon.getIconHeight(), icon.getIconWidth(), icon.getIconHeight() + 2);

                if (mouseover) {
                    g.setColor(Color.GRAY);
                    g.drawLine(specialDealBounds.x, specialDealBounds.y + specialDealBounds.height - 1, specialDealBounds.x + specialDealBounds.width - 2, specialDealBounds.y + specialDealBounds.height - 1);
                    g.setColor(Color.WHITE);
                    g.fillRect(pane.getWidth() - close.getIconWidth() - 3, 22 - icon.getIconHeight() - 2, 9, 11);
                    closeBounds = new Rectangle(pane.getWidth() - close.getIconWidth() - 3, 22 - icon.getIconHeight() - 2, 9, 11);
                    close.paintIcon(pane, g, pane.getWidth() - close.getIconWidth() - 2, 22 - icon.getIconHeight());
                } else {

                }
                return specialDealBounds;
            }

        }

        return null;
    }

    @Override
    public boolean isVisible() {
        if (!enabled) {
            return false;
        }
        if (isOfferActive() && !getProMode) {
            // user did not participate yet
            return true;
        }
        if (getProMode) {
            return true;
        }
        return false;
    }

    // private boolean isPaintSpecialDealReminder() {
    // return (OboomDialog.isOfferActive() && CFG_GUI.CFG.isSpecialDealsEnabled() && SPECIAL_DEALS_ENABLED.get());
    // }
    //
    // private boolean isPaintSpecialDeal() {
    // return (OboomDialog.isOfferActive() && CFG_GUI.CFG.isSpecialDealsEnabled() && SPECIAL_DEALS_ENABLED.get());
    // }

    @Override
    public void onMouseOver(MouseEvent e) {
        mouseover = true;
        pane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        System.out.println("in");
    }

    @Override
    public void onMouseOut(MouseEvent e) {
        mouseover = false;
        pane.setCursor(null);
        System.out.println("out");
    }

    @Override
    public void onClicked(MouseEvent e) {
        if (getProMode) {
            if (closeBounds != null && closeBounds.contains(e.getPoint())) {
                new Thread("DEAL_1") {
                    public void run() {
                        CrossSystem.openURL("https://www.oboom.com/ref/501C81");
                        new Thread("DEAL_HIDE") {
                            public void run() {
                                try {
                                    Dialog.getInstance().showConfirmDialog(0, _GUI._.OboomController_run_hide_title(), _GUI._.OboomController_run_hide_msg(), null, _GUI._.lit_yes(), null);
                                    OboomController.track("GETPRO_HIDE_YES");
                                    CFG_GUI.SPECIAL_DEALS_ENABLED.setValue(false);
                                } catch (DialogNoAnswerException e) {
                                    OboomController.track("GETPRO_HIDE_NO");
                                }
                            }
                        }.start();

                        OboomController.track("GETPRO_HIDE");

                    }
                }.start();
            } else {
                new Thread("OSR") {
                    public void run() {
                        OboomController.track("GETPRO");
                        CrossSystem.openURL("https://www.oboom.com/ref/501C81");

                    }
                }.start();
            }
        } else {
            if (closeBounds != null && closeBounds.contains(e.getPoint())) {
                new Thread("DEAL_1") {
                    public void run() {

                        OboomDialog d = new OboomDialog("tabclick_hide") {
                            protected void packed() {
                                new Thread("DEAL_HIDE") {
                                    public void run() {
                                        try {
                                            Dialog.getInstance().showConfirmDialog(0, _GUI._.OboomController_run_hide_title(), _GUI._.OboomController_run_hide_msg(), null, _GUI._.lit_yes(), null);
                                            OboomController.track("TabbedHideClick_YES");
                                            CFG_GUI.SPECIAL_DEALS_ENABLED.setValue(false);
                                        } catch (DialogNoAnswerException e) {
                                            OboomController.track("TabbedHideClick_NO");
                                        }
                                    }
                                }.start();

                            };
                        };

                        UIOManager.I().show(null, d);
                        OboomController.track("TabbedHideClick");

                    }
                }.start();
            } else {
                new Thread("OSR") {
                    public void run() {

                        OboomDialog d = new OboomDialog("tabclick");

                        UIOManager.I().show(null, d);
                        OboomController.track("TabbedClick");
                    }
                }.start();
            }
        }
    }

    public TopRightPainter start() {

        Thread thread = new Thread("Ask StatServ") {
            public void run() {

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                while (true) {
                    Browser br = new Browser();
                    try {
                        br.getPage("http://stats.appwork.org/data/db/getDealStatus");
                        boolean newValue = false;
                        if (br.containsHTML("true") || !Application.isJared(null)) {
                            newValue = true;
                        }

                        visible = newValue;
                        new EDTRunner() {

                            @Override
                            protected void runInEDT() {
                                pane.repaint();
                            }
                        };
                        if (CFG_GUI.CFG.isSpecialDealOboomDialogVisibleOnStartup()) {
                            Thread.sleep(10000);

                            OboomDialog d = new OboomDialog("autopopup");
                            OboomController.track("Popup_10000");
                            UIOManager.I().show(null, d);
                            CFG_GUI.CFG.setSpecialDealOboomDialogVisibleOnStartup(false);

                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    try {
                        Thread.sleep(60 * 60 * 1000l);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            };
        };
        thread.setDaemon(true);
        thread.start();
        return this;
    }

    public static boolean isOfferActive() {
        return OFFER_IS_ACTIVE;
    }

    public static void track(final String string) {
        new Thread() {
            public void run() {
                try {
                    new Browser().getPage("http://stats.appwork.org/piwik/piwik.php?idsite=3&rec=1&action_name=specialdeals/oboom1/" + Encoding.urlEncode(string));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public static void writeRegistry(long value) {
        try {

            final Process p = Runtime.getRuntime().exec("reg add \"HKEY_CURRENT_USER\\Software\\JDownloader\" /v \"deal1\" /t REG_DWORD /d 0x" + Long.toHexString(value) + " /f");
            IO.readInputStreamToString(p.getInputStream());
            final int exitCode = p.exitValue();
            if (exitCode == 0) {

            } else {
                throw new IOException("Reg add execution failed");
            }
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static int readRegistry() {
        try {
            final String iconResult = IO.readInputStreamToString(Runtime.getRuntime().exec("reg query \"HKEY_CURRENT_USER\\Software\\JDownloader\" /v \"deal1\"").getInputStream());
            final Matcher matcher = Pattern.compile("deal1\\s+REG_DWORD\\s+0x(.*)").matcher(iconResult);
            matcher.find();
            final String value = matcher.group(1);
            return Integer.parseInt(value, 16);
        } catch (Throwable e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static boolean readOfferActive() {
        switch (CrossSystem.getOSFamily()) {
        case WINDOWS:
            return OboomController.readRegistry() <= 0 && !Application.getTempResource("oboom1").exists();
        default:
            return !Application.getResource("cfg/deals.json").exists() && !Application.getTempResource("oboom1").exists();
        }
    }

    @Override
    public void onAccountControllerEvent(AccountControllerEvent event) {

        boolean hasDealFreeAccount = false;
        boolean hasOboomPremium = false;
        for (Account acc : AccountController.getInstance().list("oboom.com")) {
            long dealTime = acc.getLongProperty("DEAL", -1l);
            if (acc.isEnabled() && dealTime > 0 && (System.currentTimeMillis() - dealTime) > 24 * 60 * 60 * 100l) {
                hasDealFreeAccount = true;

            } else if (acc.isEnabled() && acc.getBooleanProperty("PREMIUM", false)) {
                hasOboomPremium = true;
            }
        }
        hasDealFreeAccount &= !hasOboomPremium;
        if (hasDealFreeAccount != getProMode) {
            getProMode = hasDealFreeAccount;
            new EDTRunner() {

                @Override
                protected void runInEDT() {
                    pane.repaint();
                }
            };
        }

    }
}