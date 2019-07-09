package net.gisnas.oystein.inventorylevel.model;

public enum BaseStore {

    NO_VITUSAPOTEK ("%06d"),
    SE_LLOYDSAPOTEK ("%06d"),
    BE_LLOYDSPHARMACIA ("%07d"),
    DE_RECUSANA ("%06d");

    public final String skuFormat;

    BaseStore(String skuFormat) {
        this.skuFormat = skuFormat;
    }
}
