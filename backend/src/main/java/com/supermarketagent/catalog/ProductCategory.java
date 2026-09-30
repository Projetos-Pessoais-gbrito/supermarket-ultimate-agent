package com.supermarketagent.catalog;

/** Fixed category list; the AI must pick one of these, so dashboards never see invented categories. */
public enum ProductCategory {
    HORTIFRUTI("Hortifruti"),
    CARNES_E_PEIXES("Carnes e peixes"),
    LATICINIOS_E_FRIOS("Laticínios e frios"),
    PADARIA("Padaria"),
    MERCEARIA("Mercearia"),
    CONGELADOS("Congelados"),
    DOCES_E_SNACKS("Doces e snacks"),
    BEBIDAS("Bebidas"),
    BEBIDAS_ALCOOLICAS("Bebidas alcoólicas"),
    LIMPEZA("Limpeza"),
    HIGIENE_E_BELEZA("Higiene e beleza"),
    BEBE("Bebê"),
    PET("Pet"),
    UTILIDADES("Utilidades e descartáveis"),
    OUTROS("Outros");

    private final String label;

    ProductCategory(String label) {
        this.label = label;
    }

    /** pt-BR name shown in the app. */
    public String label() {
        return label;
    }
}
