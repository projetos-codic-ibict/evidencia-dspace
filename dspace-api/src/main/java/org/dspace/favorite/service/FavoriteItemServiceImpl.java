/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.favorite.service;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.favorite.dao.FavoriteItemDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FavoriteItemServiceImpl implements FavoriteItemService {

    @Autowired
    private FavoriteItemDAO favoriteItemDAO;

    @Override
    public void addFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        favoriteItemDAO.addFavorite(context, eperson, item);
    }

    @Override
    public void removeFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        favoriteItemDAO.removeFavorite(context, eperson, item);
    }

    @Override
    public boolean isFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        return favoriteItemDAO.isFavorite(context, eperson, item);
    }

    @Override
    public List<UUID> getFavoriteItemIds(Context context, EPerson eperson) throws SQLException {
        return favoriteItemDAO.getFavoriteItemIds(context, eperson);
    }
}