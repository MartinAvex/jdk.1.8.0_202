/*
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.javafx.scene.control.skin;

import javafx.beans.value.ObservableValue;
import javafx.css.Styleable;
import javafx.geometry.*;
import javafx.scene.control.*;
import com.sun.javafx.scene.control.behavior.ComboBoxBaseBehavior;
import com.sun.javafx.scene.input.ExtendedInputMethodRequests;
import com.sun.javafx.scene.traversal.Algorithm;
import com.sun.javafx.scene.traversal.Direction;
import com.sun.javafx.scene.traversal.ParentTraversalEngine;
import com.sun.javafx.scene.traversal.TraversalContext;
import javafx.beans.InvalidationListener;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

public abstract class ComboBoxPopupControl<T> extends ComboBoxBaseSkin<T> {

    protected PopupControl popup;
    public static final String COMBO_BOX_STYLE_CLASS = "combo-box-popup";

    private boolean popupNeedsReconfiguring = true;

    private final ComboBoxBase<T> comboBoxBase;
    private TextField textField;

    public ComboBoxPopupControl(ComboBoxBase<T> comboBoxBase, final ComboBoxBaseBehavior<T> behavior) {
        super(comboBoxBase, behavior);
        this.comboBoxBase = comboBoxBase;

        // editable input node
        this.textField = getEditor() != null ? getEditableInputNode() : null;

        // Fix for RT-29565. Without this the textField does not have a correct
        // pref width at startup, as it is not part of the scenegraph (and therefore
        // has no pref width until after the first measurements have been taken).
        if (this.textField != null) {
            getChildren().add(textField);
        }

        // move fake focus in to the textfield if the comboBox is editable
        comboBoxBase.focusedProperty().addListener((ov, t, hasFocus) -> {
            if (getEditor() != null) {
                // Fix for the regression noted in a comment in RT-29885.
                ((FakeFocusTextField)textField).setFakeFocus(hasFocus);

                // JDK-8120120 (aka RT-21454) and JDK-8136838
                if (!hasFocus) {
                    setTextFromTextFieldIntoComboBoxValue();
                }
            }
        });

        comboBoxBase.addEventFilter(KeyEvent.ANY, ke -> {
            if (textField == null || getEditor() == null) {
                handleKeyEvent(ke, false);
            } else {
                // This prevents a stack overflow from our rebroadcasting of the
                // event to the textfield that occurs in the final else statement
                // of the conditions below.
                if (ke.getTarget().equals(textField)) return;

                switch (ke.getCode()) {
                  case ESCAPE:
                  case F4:
                  case F10:
                      // Allow to bubble up.
                      break;

                  case ENTER:
                    handleKeyEvent(ke, true);
                    break;

                  default:
                    // Fix for the regression noted in a comment in RT-29885.
                    // This forwards the event down into the TextField when
                    // the key event is actually received by the ComboBox.
                    textField.fireEvent(ke.copyFor(textField, textField));
                    ke.consume();
                }
            }
        });

        // RT-38978: Forward input method events to TextField if editable.
        if (comboBoxBase.getOnInputMethodTextChanged() == null) {
            comboBoxBase.setOnInputMethodTextChanged(event -> {
                if (textField != null && getEditor() != null && comboBoxBase.getScene().getFocusOwner() == comboBoxBase) {
                    if (textField.getOnInputMethodTextChanged() != null) {
                        textField.getOnInputMethodTextChanged().handle(event);
                    }
                }
            });
        }

        // Fix for RT-36902, where focus traversal was getting stuck inside the ComboBox
        comboBoxBase.setImpl_traversalEngine(new ParentTraversalEngine(comboBoxBase, new Algorithm() {
            @Override public Node select(Node owner, Direction dir, TraversalContext context) {
                return null;
            }

            @Override public Node selectFirst(TraversalContext context) {
                return null;
            }

            @Override public Node selectLast(TraversalContext context) {
                return null;
            }
        }));

        updateEditable();
    }

    /**
     * This method should return the Node that will be displayed when the user
     * clicks on the ComboBox 'button' area.
     */
    protected abstract Node getPopupContent();

    protected PopupControl getPopup() {
        if (popup == null) {
            createPopup();
        }
        return popup;
    }

    @Override public void show() {
        if (getSkinnable() == null) {
            throw new IllegalStateException("ComboBox is null");
        }

        Node content = getPopupContent();
        if (content == null) {
            throw new IllegalStateException("Popup node is null");
        }

        if (getPopup().isShowing()) return;

        positionAndShowPopup();
    }

    @Override public void hide() {
        if (popup != null && popup.isShowing()) {
            popup.hide();
        }
    }

    private Point2D getPrefPopupPosition() {
        return com.sun.javafx.util.Utils.pointRelativeTo(getSkinnable(), getPopupContent(), HPos.CENTER, VPos.BOTTOM, 0, 0, true);
    }

    private void positionAndShowPopup() {
        final PopupControl _popup = getPopup();
        _popup.getScene().setNodeOrientation(getSkinnable().getEffectiveNodeOrientation());


        final Node popupContent = getPopupContent();
        sizePopup();

        Point2D p = getPrefPopupPosition();

        popupNeedsReconfiguring = true;
        reconfigurePopup();

        final ComboBoxBase<T> comboBoxBase = getSkinnable();
        _popup.show(comboBoxBase.getScene().getWindow(),
                snapPosition(p.getX()),
                snapPosition(p.getY()));

        popupContent.requestFocus();

        // second call to sizePopup here to enable proper sizing _after_ the popup
        // has been displayed. See RT-37622 for more detail.
        sizePopup();
    }

    private void sizePopup() {
        final Node popupContent = getPopupContent();

        if (popupContent instanceof Region) {
            // snap to pixel
            final Region r = (Region) popupContent;

            // 0 is used here for the width due to RT-46097
            double prefHeight = snapSize(r.prefHeight(0));
            double minHeight = snapSize(r.minHeight(0));
            double maxHeight = snapSize(r.maxHeight(0));
            double h = snapSize(Math.min(Math.max(prefHeight, minHeight), Math.max(minHeight, maxHeight)));

            double prefWidth = snapSize(r.prefWidth(h));
            double minWidth = snapSize(r.minWidth(h));
            double maxWidth = snapSize(r.maxWidth(h));
            double w = snapSize(Math.min(Math.max(prefWidth, minWidth), Math.max(minWidth, maxWidth)));

            popupContent.resize(w, h);
        } else {
            popupContent.autosize();
        }
    }

    private void createPopup() {
        popup = new PopupControl() {

            @Override public Styleable getStyleableParent() {
                return ComboBoxPopupControl.this.getSkinnable();
            }
            {
                setSkin(new Skin<Skinnable>() {
                    @Override public Skinnable getSkinnable() { return ComboBoxPopupControl.this.getSkinnable(); }
                    @Override public Node getNode() { return getPopupContent(); }
                    @Override public void dispose() { }
                });
            }

        };
        popup.getStyleClass().add(COMBO_BOX_STYLE_CLASS);
        popup.setConsumeAutoHidingEvents(false);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setOnAutoHide(e -> {
            getBehavior().onAutoHide();
        });
        popup.addEventHandler(MouseEvent.MOUSE_CLICKED, t -> {
            // RT-18529: We listen to mouse input that is received by the popup
            // but that is not consumed, and assume that this is due to the mouse
            // clicking outside of the node, but in areas such as the
            // dropshadow.
            getBehavior().onAutoHide();
        });
        popup.addEventHandler(WindowEvent.WINDOW_HIDDEN, t -> {
            // Make sure the accessibility focus returns to the combo box
            // after the window closes.
            getSkinnable().notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_NODE);
        });

        // Fix for RT-21207
        InvalidationListener layoutPosListener = o -> {
            popupNeedsReconfiguring = true;
            reconfigurePopup();
        };
        getSkinnable().layoutXProperty().addListener(layoutPosListener);
        getSkinnable().layoutYProperty().addListener(layoutPosListener);
        getSkinnable().widthProperty().addListener(layoutPosListener);
        getSkinnable().heightProperty().addListener(layoutPosListener);

        // RT-36966 - if skinnable's scene becomes null, ensure popup is closed
        getSkinnable().sceneProperty().addListener(o -> {
            if (((ObservableValue)o).getValue() == null) {
                hide();
            }
        });

    }

    void reconfigurePopup() {
        // RT-26861. Don't call getPopup() here because it may cause the popup
        // to be created too early, which leads to memory leaks like those noted
        // in RT-32827.
        if (popup == null) return;

        final boolean isShowing = popup.isShowing();
        if (! isShowing) return;

        if (! popupNeedsReconfiguring) return;
        popupNeedsReconfiguring = false;

        final Point2D p = getPrefPopupPosition();

        final Node popupContent = getPopupContent();
        final double minWidth = popupContent.prefWidth(Region.USE_COMPUTED_SIZE);
        final double minHeight = popupContent.prefHeight(Region.USE_COMPUTED_SIZE);

        if (p.getX() > -1) popup.setAnchorX(p.getX());
        if (p.getY() > -1) popup.setAnchorY(p.getY());
        if (minWidth > -1) popup.setMinWidth(minWidth);
        if (minHeight > -1) popup.setMinHeight(minHeight);

        final Bounds b = popupContent.getLayoutBounds();
        final double currentWidth = b.getWidth();
        final double currentHeight = b.getHeight();
        final double newWidth  = currentWidth < minWidth ? minWidth : currentWidth;
        final double newHeight = currentHeight < minHeight ? minHeight : currentHeight;

        if (newWidth != currentWidth || newHeight != currentHeight) {
            // Resizing content to resolve issues such as RT-32582 and RT-33700
            // (where RT-33700 was introduced due to a previous fix for RT-32582)
            popupContent.resize(newWidth, newHeight);
            if (popupContent instanceof Region) {
                ((Region)popupContent).setMinSize(newWidth, newHeight);
                ((Region)popupContent).setPrefSize(newWidth, newHeight);
            }
        }
    }





    /***************************************************************************
     *                                                                         *
     * TextField Listeners                                                     *
     *                                                                         *
     **************************************************************************/

    private EventHandler<MouseEvent> textFieldMouseEventHandler = event -> {
        ComboBoxBase<T> comboBoxBase = getSkinnable();
        if (!event.getTarget().equals(comboBoxBase)) {
            comboBoxBase.fireEvent(event.copyFor(comboBoxBase, comboBoxBase));
            event.consume();
        }
    };
    private EventHandler<DragEvent> textFieldDragEventHandler = event -> {
        ComboBoxBase<T> comboBoxBase = getSkinnable();
        if (!event.getTarget().equals(comboBoxBase)) {
            comboBoxBase.fireEvent(event.copyFor(comboBoxBase, comboBoxBase));
            event.consume();
        }
    };


    /**
     * Subclasses are responsible for getting the editor. This will be removed
     * in FX 9 when the editor property is moved up to ComboBoxBase.
     *
     * Note: ComboBoxListViewSkin should return null if editable is false, even
     * if the ComboBox does have an editor set.
     */
    protected abstract TextField getEditor();

    /**
     * Subclasses are responsible for getting the converter. This will be
     * removed in FX 9 when the converter property is moved up to ComboBoxBase.
     */
    protected abstract StringConverter<T> getConverter();

    private String initialTextFieldValue = null;
    protected TextField getEditableInputNode() {
        if (textField == null && getEditor() != null) {
            textField = getEditor();
            textField.setFocusTraversable(false);
            textField.promptTextProperty().bind(comboBoxBase.promptTextProperty());
            textField.tooltipProperty().bind(comboBoxBase.tooltipProperty());

            // Fix for RT-21406: ComboBox do not show initial text value
            initialTextFieldValue = textField.getText();
            // End of fix (see updateDisplayNode below for the related code)
        }

        return textField;
    }

    protected void setTextFromTextFieldIntoComboBoxValue() {
        if (getEditor() != null) {
            StringConverter<T> c = getConverter();
            if (c != null) {
                T oldValue = comboBoxBase.getValue();
                T value = oldValue;
                String text = textField.getText();

                // conditional check here added due to RT-28245
                if (oldValue == null && (text == null || text.isEmpty())) {
                    value = null;
                } else {
                    try {
                        value = c.fromString(text);
                    } catch (Exception ex) {
                        // Most likely a parsing error, such as DateTimeParseException
                    }
                }

                if ((value != null || oldValue != null) && (value == null || !value.equals(oldValue))) {
                    // no point updating values needlessly if they are the same
                    comboBoxBase.setValue(value);
                }

                updateDisplayNode();
            }
        }
    }

    protected void updateDisplayNode() {
        if (textField != null && getEditor() != null) {
            T value = comboBoxBase.getValue();
            StringConverter<T> c = getConverter();

            if (initialTextFieldValue != null && ! initialTextFieldValue.isEmpty()) {
                // Remainder of fix for RT-21406: ComboBox do not show initial text value
                textField.setText(initialTextFieldValue);
                initialTextFieldValue = null;
                // end of fix
            } else {
                String stringValue = c.toString(value);
                if (value == null || stringValue == null) {
                    textField.setText("");
                } else if (! stringValue.equals(textField.getText())) {
                    textField.setText(stringValue);
                }
            }
        }
    }


    private void handleKeyEvent(KeyEvent ke, boolean doConsume) {
        // When the user hits the enter or F4 keys, we respond before
        // ever giving the event to the TextField.
        if (ke.getCode() == KeyCode.ENTER) {
            setTextFromTextFieldIntoComboBoxValue();

            if (doConsume && comboBoxBase.getOnAction() != null) {
                ke.consume();
            } else {
                forwardToParent(ke);
            }
        } else if (ke.getCode() == KeyCode.F4) {
            if (ke.getEventType() == KeyEvent.KEY_RELEASED) {
                if (comboBoxBase.isShowing()) comboBoxBase.hide();
                else comboBoxBase.show();
            }
            ke.consume(); // we always do a consume here (otherwise unit tests fail)
        }
    }

    private void forwardToParent(KeyEvent event) {
        if (comboBoxBase.getParent() != null) {
            comboBoxBase.getParent().fireEvent(event);
        }
    }

    protected void updateEditable() {
        TextField newTextField = getEditor();

        if (getEditor() == null) {
            // remove event filters
            if (textField != null) {
                textField.removeEventFilter(MouseEvent.DRAG_DETECTED, textFieldMouseEventHandler);
                textField.removeEventFilter(DragEvent.ANY, textFieldDragEventHandler);

                comboBoxBase.setInputMethodRequests(null);
            }
        } else if (newTextField != null) {
            // add event filters

            // Fix for RT-31093 - drag events from the textfield were not surfacing
            // properly for the ComboBox.
            newTextField.addEventFilter(MouseEvent.DRAG_DETECTED, textFieldMouseEventHandler);
            newTextField.addEventFilter(DragEvent.ANY, textFieldDragEventHandler);

            // RT-38978: Forward input method requests to TextField.
            comboBoxBase.setInputMethodRequests(new ExtendedInputMethodRequests() {
                @Override public Point2D getTextLocation(int offset) {
                    return newTextField.getInputMethodRequests().getTextLocation(offset);
                }

                @Override public int getLocationOffset(int x, int y) {
                    return newTextField.getInputMethodRequests().getLocationOffset(x, y);
                }

                @Override public void cancelLatestCommittedText() {
                    newTextField.getInputMethodRequests().cancelLatestCommittedText();
                }

                @Override public String getSelectedText() {
                    return newTextField.getInputMethodRequests().getSelectedText();
                }

                @Override public int getInsertPositionOffset() {
                    return ((ExtendedInputMethodRequests)newTextField.getInputMethodRequests()).getInsertPositionOffset();
                }

                @Override public String getCommittedText(int begin, int end) {
                    return ((ExtendedInputMethodRequests)newTextField.getInputMethodRequests()).getCommittedText(begin, end);
                }

                @Override public int getCommittedTextLength() {
                    return ((ExtendedInputMethodRequests)newTextField.getInputMethodRequests()).getCommittedTextLength();
                }
            });
        }

        textField = newTextField;
    }

    /***************************************************************************
     *                                                                         *
     * Support classes                                                         *
     *                                                                         *
     **************************************************************************/

    public static final class FakeFocusTextField extends TextField {

        @Override public void requestFocus() {
            if (getParent() != null) {
                getParent().requestFocus();
            }
        }

        public void setFakeFocus(boolean b) {
            setFocused(b);
        }

        @Override
        public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
            switch (attribute) {
                case FOCUS_ITEM:
                    /* Internally comboBox reassign its focus the text field.
                     * For the accessibility perspective it is more meaningful
                     * if the focus stays with the comboBox control.
                     */
                    return getParent();
                default: return super.queryAccessibleAttribute(attribute, parameters);
            }
        }
    }



    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

}
