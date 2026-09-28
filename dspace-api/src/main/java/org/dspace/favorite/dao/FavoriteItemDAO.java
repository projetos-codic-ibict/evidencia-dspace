/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.favorite.dao;

import java.sql.SQLException;
import java.util.List;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;

public interface FavoriteItemDAO {
    void addFavorite(Context context, EPerson eperson, Item item) throws SQLException;
    void removeFavorite(Context context, EPerson eperson, Item item) throws SQLException;
    boolean isFavorite(Context context, EPerson eperson, Item item) throws SQLException;
    List<Item> getFavoriteItems(Context context, EPerson eperson) throws SQLException;
}