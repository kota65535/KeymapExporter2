package com.kota65535.intellij.plugin.keymap.exporter2;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.kota65535.intellij.plugin.keymap.exporter2.xml.Action;
import com.kota65535.intellij.plugin.keymap.exporter2.sheet.KeyboardWorkbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by tozawa on 2017/03/08.
 */
public class KeyboardWorkbookWriter {
    private KeyboardWorkbook workbook;
    private static final Logger logger = Logger.getInstance(ExportKeymapAction.class);

    Document document;
    String outputFileName;


    public KeyboardWorkbookWriter(String inputFileName, Document document, String outputFileName) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(inputFileName);
        try {
            workbook = new KeyboardWorkbook(is);
        } catch (Exception ex) {
            logger.error("Failed to load " + inputFileName);
            ex.printStackTrace();
        }
        this.document = document;
        this.outputFileName = outputFileName;
    }


    /**
     * create key to action-id map from XML document.
     * @return
     */
    private Map<String, List<Action>> getKey2Actions() {
        Map<String, List<Action>> key2Actions = new HashMap<>();
        NodeList nodeList = document.getElementsByTagName("action");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element elem = (Element) nodeList.item(i);
            String keyStroke = elem.getAttribute("key");
            if (key2Actions.get(keyStroke) != null) {
                key2Actions.get(keyStroke).add(new Action(
                        elem.getAttribute("id"),
                        elem.getAttribute("text"),
                        elem.getAttribute("color")));
            } else {
                key2Actions.put(keyStroke, Lists.newArrayList(new Action(
                        elem.getAttribute("id"),
                        elem.getAttribute("text"),
                        elem.getAttribute("color"))));
            }
        }

        return key2Actions;
    }


    /**
     * if multiple actions are associated with single key, choose only actions that belong to MainMenu group.
     */
    private Map<String, List<Action>> getKey2MainActions(int threshold) {
        Map<String, List<Action>> key2Actions = getKey2Actions();

        return key2Actions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            if (entry.getValue().size() > threshold) {
                                return entry.getValue().stream()
                                        .filter(a -> isGroupOf(a.getActionId(), "MainMenu"))
                                        .collect(Collectors.toList());
                            } else {
                                return entry.getValue();
                            }
                        }));
    }


    private boolean isGroupOf(String childId, String ancestorId) {

        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = String.format("/root//group[@id='%s']//action[@id='%s']", ancestorId, childId);
        try {
            Node widgetNode = (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
            if (widgetNode != null) {
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }


    public void write() {
        Map<String, List<Action>> key2MainAct = getKey2MainActions(2);

        key2MainAct.forEach((key, value) -> {
            List<Action> list = new ArrayList<>(value);
            if (value.size() == 2) {
                workbook.setKeyboardCell(key,
                        list.get(0).getText(),
                        new XSSFColor(new Color(list.get(0).getColor())),
                        list.get(1).getText(),
                        new XSSFColor(new Color(list.get(1).getColor())));
            } else if (value.size() == 1) {
                workbook.setKeyboardCell(key,
                        list.get(0).getText(),
                        new XSSFColor(new Color(list.get(0).getColor())));
            }
        });

        try {
            workbook.save(outputFileName);
            logger.info(String.format("Successfully print file %s", outputFileName));
        } catch (IOException ex) {
            logger.error(String.format("Failed to print file", outputFileName), ex);
        }
    }
}

