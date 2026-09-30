/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Item;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.favorite.service.FavoriteItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Favoritos do usuário logado. O frontend guarda só os IDs (GET /ids) e carrega cada item pelo endpoint
 * padrão de itens, então aqui não há listagem de itens completos.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteItemRestController {

    @Autowired
    private FavoriteItemService favoriteItemService;

    @Autowired
    private ItemService itemService;

    @Autowired
    private AuthorizeService authorizeService;

    @GetMapping("/ids")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getUserFavoriteIds(HttpServletRequest request) throws SQLException {
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        List<String> ids = favoriteItemService.getFavoriteItemIds(context, currentUser).stream()
                .map(UUID::toString)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ids);
    }

    @PostMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addFavorite(@PathVariable String itemId, HttpServletRequest request) throws Exception {
        UUID itemUuid = parseUuid(itemId);
        if (itemUuid == null) {
            return ResponseEntity.badRequest().build();
        }
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, itemUuid);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        if (!authorizeService.authorizeActionBoolean(context, item, Constants.READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        favoriteItemService.addFavorite(context, currentUser, item);
        context.commit();

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> removeFavorite(@PathVariable String itemId, HttpServletRequest request) throws Exception {
        UUID itemUuid = parseUuid(itemId);
        if (itemUuid == null) {
            return ResponseEntity.badRequest().build();
        }
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, itemUuid);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }

        favoriteItemService.removeFavorite(context, currentUser, item);
        context.commit();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable String itemId, HttpServletRequest request)
        throws Exception {
        UUID itemUuid = parseUuid(itemId);
        if (itemUuid == null) {
            return ResponseEntity.badRequest().build();
        }
        Context context = ContextUtil.obtainContext(request);
        EPerson currentUser = context.getCurrentUser();

        Item item = itemService.find(context, itemUuid);
        if (item == null) {
            return ResponseEntity.ok(false);
        }

        return ResponseEntity.ok(favoriteItemService.isFavorite(context, currentUser, item));
    }

    /** Devolve null quando o texto não é um UUID, para o endpoint responder 400 em vez de 500. */
    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
