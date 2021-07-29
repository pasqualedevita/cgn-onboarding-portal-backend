DROP MATERIALIZED VIEW IF EXISTS offline_merchant;

CREATE MATERIALIZED VIEW offline_merchant AS
WITH merchant AS (
    SELECT a.agreement_k,
       COALESCE( NULLIF(p.name, ''), p.full_name) AS name
    FROM agreement a
             JOIN profile p ON (p.agreement_fk = a.agreement_k)
    WHERE a.state = 'APPROVED'
      AND a.start_date <= CURRENT_TIMESTAMP
      AND CURRENT_TIMESTAMP <= a.end_date
      AND p.sales_channel IN ('OFFLINE', 'BOTH')
),
     product_categories AS (
         SELECT DISTINCT d.agreement_fk,
                         pc.product_category
         FROM discount d
                  JOIN discount_product_category pc ON (d.discount_k = pc.discount_fk)
         WHERE d.state = 'PUBLISHED'
           AND d.start_date <= CURRENT_TIMESTAMP
           AND CURRENT_TIMESTAMP <= d.end_date
           AND EXISTS(SELECT 1 FROM merchant m WHERE m.agreement_k = d.agreement_fk)
     ),
     merchant_with_categories AS (
         SELECT m.agreement_k,
                m.name,
                pc.product_category,
                CASE
                    WHEN pc.product_category = 'ENTERTAINMENT' THEN TRUE
                    ELSE FALSE
                    END AS entertainment,
                CASE
                    WHEN pc.product_category = 'TRAVELLING' THEN TRUE
                    ELSE FALSE
                    END AS travelling,
                CASE
                    WHEN pc.product_category = 'FOOD_DRINK' THEN TRUE
                    ELSE FALSE
                    END AS food_drink,
                CASE
                    WHEN pc.product_category = 'SERVICES' THEN TRUE
                    ELSE FALSE
                    END AS services,
                CASE
                    WHEN pc.product_category = 'LEARNING' THEN TRUE
                    ELSE FALSE
                    END AS learning,
                CASE
                    WHEN pc.product_category = 'HOTELS' THEN TRUE
                    ELSE FALSE
                    END AS hotels,
                CASE
                    WHEN pc.product_category = 'SPORTS' THEN TRUE
                    ELSE FALSE
                    END AS sports,
                CASE
                    WHEN pc.product_category = 'HEALTH' THEN TRUE
                    ELSE FALSE
                    END AS health,
                CASE
                    WHEN pc.product_category = 'SHOPPING' THEN TRUE
                    ELSE FALSE
                    END AS shopping
         FROM merchant m
                  JOIN product_categories pc ON (m.agreement_k = pc.agreement_fk)
     ),
     merchant_without_address AS (
        SELECT m.agreement_k                 AS id,
               m.name,
               array_agg(m.product_category) AS product_categories,
               lower(m.name)                 AS searchable_name,
               bool_or(m.entertainment)      AS entertainment,
               bool_or(m.travelling)         AS travelling,
               bool_or(m.food_drink)         AS food_drink,
               bool_or(m.services)           AS services,
               bool_or(m.learning)           AS learning,
               bool_or(m.hotels)             AS hotels,
               bool_or(m.sports)             AS sports,
               bool_or(m.health)             AS health,
               bool_or(m.shopping)           AS shopping
        FROM merchant_with_categories m
        GROUP BY 1, 2
     )
     SELECT m.id,
            m.name,
            m.product_categories,
            m.searchable_name,
            m.entertainment,
            m.travelling,
            m.food_drink,
            m.services,
            m.learning,
            m.hotels,
            m.sports,
            m.health,
            m.shopping,
            a.full_address,
            a.latitude,
            a.longitude,
            a.address_k AS address_id,
            now()		AS last_update
     FROM merchant_without_address m
     JOIN profile p on m.id = p.agreement_fk
     JOIN address a ON p.profile_k = a.profile_fk;

CREATE UNIQUE INDEX offline_merchant_id_unique_idx ON offline_merchant (id, address_id);

CREATE INDEX IF NOT EXISTS idx_offline_merchant_search_name ON offline_merchant USING gin (searchable_name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_offline_merchant_lat_lon on offline_merchant (latitude, longitude);