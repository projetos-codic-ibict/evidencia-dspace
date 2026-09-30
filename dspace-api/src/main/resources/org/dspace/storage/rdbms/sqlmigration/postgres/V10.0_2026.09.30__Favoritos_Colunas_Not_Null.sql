--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- A UNIQUE (eperson_id, item_id) não protege linhas com NULL
ALTER TABLE user_favorite_item ALTER COLUMN eperson_id SET NOT NULL;
ALTER TABLE user_favorite_item ALTER COLUMN item_id SET NOT NULL;
ALTER TABLE user_favorite_item ALTER COLUMN created_at SET NOT NULL;
