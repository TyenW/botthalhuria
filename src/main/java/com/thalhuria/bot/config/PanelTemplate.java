package com.thalhuria.bot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Mensagem fixa (painel) que o bot publica e mantém atualizada em um canal.
 * Tipos: EMBED (somente texto), VERIFY (botão de verificação), SELF_ROLES (menu de cargos), TICKET (botão de abrir ticket).
 * Textos aceitam placeholders: {server}, {brand}, {channel:KEY}, {role:KEY}.
 */
public class PanelTemplate {

    public static class Field {
        private String name;
        private String value;
        private boolean inline = false;

        public Field() {}

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isInline() {
            return inline;
        }

        public void setInline(boolean inline) {
            this.inline = inline;
        }
    }

    private String type = "EMBED";
    private String title;
    private String description;
    private String colorHex;
    private String imageUrl;
    private String thumbnailUrl;
    private List<Field> fields = new ArrayList<>();
    private String buttonLabel;
    private String buttonEmoji;
    private String placeholder;
    private List<PanelButton> buttons = new ArrayList<>();

    public PanelTemplate() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    public void setButtonLabel(String buttonLabel) {
        this.buttonLabel = buttonLabel;
    }

    public String getButtonEmoji() {
        return buttonEmoji;
    }

    public void setButtonEmoji(String buttonEmoji) {
        this.buttonEmoji = buttonEmoji;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public List<PanelButton> getButtons() {
        return buttons;
    }

    public void setButtons(List<PanelButton> buttons) {
        this.buttons = buttons != null ? buttons : new ArrayList<>();
    }
}
