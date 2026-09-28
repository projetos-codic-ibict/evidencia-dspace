/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.HashMap; // Se o seu serviço estiver em outro pacote, ajuste aqui
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dspace.app.rest.converter.ConverterService;
import org.dspace.app.rest.model.ItemRest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.app.rest.utils.Utils;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.favorite.service.FavoriteItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/favorites") 
public class FavoriteItemRestController {

    @Autowired
    private FavoriteItemService favoriteItemService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ConverterService converterService;

    @Autowired
    private Utils utils;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getUserFavorites(HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        List<Item> favoriteItems = favoriteItemService.getFavoriteItems(context, currentUser);

        List<ItemRest> itemsRest = favoriteItems.stream()
                .map(item -> (ItemRest) converterService.toRest(item, utils.obtainProjection()))
                .collect(Collectors.toList());

        Map<String, Object> embedded = new HashMap<>();
        embedded.put("favorites", itemsRest);
        Map<String, Object> response = new HashMap<>();
        response.put("_embedded", embedded);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addFavorite(@PathVariable String itemId, HttpServletRequest request) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, UUID.fromString(itemId));
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        favoriteItemService.addFavorite(context, currentUser, item);
        context.commit();

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeFavorite(@PathVariable String itemId, HttpServletRequest request) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, UUID.fromString(itemId));
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        favoriteItemService.removeFavorite(context, currentUser, item);
        context.commit();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable String itemId, HttpServletRequest request) throws Exception {
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, UUID.fromString(itemId));
        if (item == null) {
            return ResponseEntity.ok(false);
        }

        boolean isFav = favoriteItemService.isFavorite(context, currentUser, item);
        return ResponseEntity.ok(isFav);
    }
}