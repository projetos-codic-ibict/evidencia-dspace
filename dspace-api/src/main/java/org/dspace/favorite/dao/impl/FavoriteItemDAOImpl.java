/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.favorite.dao.impl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.AbstractHibernateDAO;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.favorite.dao.FavoriteItemDAO;
import org.hibernate.query.NativeQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings({"rawtypes", "deprecation"})
public class FavoriteItemDAOImpl extends AbstractHibernateDAO<Item> implements FavoriteItemDAO {

    @Autowired
    private ItemService itemService;

    protected FavoriteItemDAOImpl() {
        super();
    }

    @Override
    public void addFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        if (!isFavorite(context, eperson, item)) {
            String sql = "INSERT INTO user_favorite_item (eperson_id, item_id) VALUES (:epersonId, :itemId)";
            NativeQuery query = getHibernateSession(context).createNativeQuery(sql);
            query.setParameter("epersonId", eperson.getID());
            query.setParameter("itemId", item.getID());
            query.executeUpdate();
        }
    }

    @Override
    public void removeFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        String sql = "DELETE FROM user_favorite_item WHERE eperson_id = :epersonId AND item_id = :itemId";
        NativeQuery query = getHibernateSession(context).createNativeQuery(sql);
        query.setParameter("epersonId", eperson.getID());
        query.setParameter("itemId", item.getID());
        query.executeUpdate();
    }

    @Override
    public boolean isFavorite(Context context, EPerson eperson, Item item) throws SQLException {
        String sql = "SELECT id FROM user_favorite_item WHERE eperson_id = :epersonId AND item_id = :itemId";
        NativeQuery query = getHibernateSession(context).createNativeQuery(sql);
        query.setParameter("epersonId", eperson.getID());
        query.setParameter("itemId", item.getID());
        return !query.getResultList().isEmpty();
    }

    @Override
    public List<Item> getFavoriteItems(Context context, EPerson eperson) throws SQLException {
        String sql = "SELECT item_id FROM user_favorite_item WHERE eperson_id = :epersonId ORDER BY created_at DESC";
        NativeQuery query = getHibernateSession(context).createNativeQuery(sql);
        query.setParameter("epersonId", eperson.getID());

        List<Item> favorites = new ArrayList<>();
        List<?> results = query.getResultList();

        for (Object result : results) {
            UUID itemId = null;
            if (result instanceof UUID) {
                itemId = (UUID) result;
            } else if (result != null) {
                itemId = UUID.fromString(result.toString());
            }

            if (itemId != null) {
                Item foundItem = itemService.find(context, itemId);
                if (foundItem != null) {
                    favorites.add(foundItem);
                }
            }
        }
        return favorites;
    }

    @Override
    public List<UUID> getFavoriteItemIds(Context context, EPerson eperson) throws SQLException {
        String sql = "SELECT item_id FROM user_favorite_item WHERE eperson_id = :epersonId ORDER BY created_at DESC";
        NativeQuery query = getHibernateSession(context).createNativeQuery(sql);
        query.setParameter("epersonId", eperson.getID());

        List<UUID> ids = new ArrayList<>();
        for (Object result : query.getResultList()) {
            if (result instanceof UUID) {
                ids.add((UUID) result);
            } else if (result != null) {
                ids.add(UUID.fromString(result.toString()));
            }
        }
        return ids;
    }
}