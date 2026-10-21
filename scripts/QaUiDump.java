import android.app.UiAutomation;
import android.graphics.Rect;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.StringWriter;
import org.xmlpull.v1.XmlSerializer;

/** Shell-only QA helper. Reads a fresh hierarchy without waiting for a blinking cursor to stop. */
public final class QaUiDump {
    public static void main(String[] args) {
        try {
            dump();
        } catch (Exception error) {
            System.err.println(error.toString());
            System.exit(1);
        }
    }

    private static void dump() throws Exception {
        Class<?> type = Class.forName("com.android.uiautomator.core.UiAutomationShellWrapper");
        Object wrapper = type.getDeclaredConstructor().newInstance();
        type.getMethod("connect").invoke(wrapper);
        try {
            UiAutomation automation = (UiAutomation) type.getMethod("getUiAutomation").invoke(wrapper);
            AccessibilityNodeInfo root = null;
            for (int i = 0; i < 20 && root == null; i++) {
                Thread.sleep(100);
                root = automation.getRootInActiveWindow();
            }
            if (root == null) throw new IllegalStateException("No active Android window");
            StringWriter output = new StringWriter();
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output);
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "hierarchy");
            write(xml, root);
            xml.endTag(null, "hierarchy");
            xml.endDocument();
            System.out.println(output);
        } finally {
            type.getMethod("disconnect").invoke(wrapper);
        }
        System.exit(0);
    }

    private static void write(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        xml.startTag(null, "node");
        xml.attribute(null, "package", value(node.getPackageName()));
        xml.attribute(null, "class", value(node.getClassName()));
        xml.attribute(null, "text", value(node.getText()));
        xml.attribute(null, "content-desc", value(node.getContentDescription()));
        xml.attribute(null, "resource-id", value(node.getViewIdResourceName()));
        xml.attribute(null, "enabled", String.valueOf(node.isEnabled()));
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        xml.attribute(null, "bounds", bounds.toShortString());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                write(xml, child);
                child.recycle();
            }
        }
        xml.endTag(null, "node");
    }

    private static String value(CharSequence text) {
        return text == null ? "" : text.toString();
    }
}
