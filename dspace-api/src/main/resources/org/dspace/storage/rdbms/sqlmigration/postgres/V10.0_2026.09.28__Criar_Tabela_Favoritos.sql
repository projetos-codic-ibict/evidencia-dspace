--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- 1. Criação da Tabela Relacional
CREATE SEQUENCE user_favorite_item_seq;

CREATE TABLE user_favorite_item (
    id INTEGER PRIMARY KEY DEFAULT nextval('user_favorite_item_seq'),
    eperson_id UUID REFERENCES eperson(uuid) ON DELETE CASCADE,
    item_id UUID REFERENCES item(uuid) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (eperson_id, item_id)
);

CREATE INDEX idx_user_favorite_eperson ON user_favorite_item(eperson_id);
CREATE INDEX idx_user_favorite_item ON user_favorite_item(item_id);