CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS categories (
  category_title text NOT NULL,
  slug text NOT NULL UNIQUE,
  icon_url text,
  uuid uuid PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS uuid uuid;

UPDATE categories
SET uuid = COALESCE(uuid, (to_jsonb(categories)->>'id')::uuid)
WHERE uuid IS NULL AND (to_jsonb(categories) ? 'id');

ALTER TABLE categories
  ALTER COLUMN uuid SET NOT NULL;

ALTER TABLE categories
  DROP COLUMN IF EXISTS id;

ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS category_title text;

ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS icon_url text;

UPDATE categories
SET category_title = initcap(replace(slug, '-', ' '))
WHERE category_title IS NULL;

ALTER TABLE categories
  ALTER COLUMN category_title SET NOT NULL;

CREATE TABLE IF NOT EXISTS subcategories (
  uuid uuid PRIMARY KEY,
  sub_category_title text NOT NULL,
  sub_category_slug text NOT NULL UNIQUE,
  category_id uuid NOT NULL REFERENCES categories(uuid) ON UPDATE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE subcategories
  ADD COLUMN IF NOT EXISTS sub_category_title text;

UPDATE subcategories
SET sub_category_title = initcap(replace(sub_category_slug, '-', ' '))
WHERE sub_category_title IS NULL;

ALTER TABLE subcategories
  ALTER COLUMN sub_category_title SET NOT NULL;

ALTER TABLE subcategories
  ADD COLUMN IF NOT EXISTS sub_category_slug text;

UPDATE subcategories
SET sub_category_slug = COALESCE(sub_category_slug, to_jsonb(subcategories)->>'slug')
WHERE sub_category_slug IS NULL AND (to_jsonb(subcategories) ? 'slug');

ALTER TABLE subcategories
  ALTER COLUMN sub_category_slug SET NOT NULL;

ALTER TABLE subcategories
  DROP COLUMN IF EXISTS slug;

CREATE TABLE IF NOT EXISTS leaf_categories (
  id uuid PRIMARY KEY,
  category_id uuid NOT NULL REFERENCES categories(uuid) ON UPDATE CASCADE,
  subcategory_id uuid NOT NULL REFERENCES subcategories(uuid) ON UPDATE CASCADE,
  leaf_category_title text,
  leaf_category_slug text,
  slug text NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE leaf_categories
  ADD COLUMN IF NOT EXISTS leaf_category_title text,
  ADD COLUMN IF NOT EXISTS leaf_category_slug text;

UPDATE leaf_categories
SET leaf_category_slug = slug
WHERE leaf_category_slug IS NULL;

UPDATE leaf_categories
SET leaf_category_title = initcap(replace(leaf_category_slug, '-', ' '))
WHERE leaf_category_title IS NULL;

ALTER TABLE leaf_categories
  ALTER COLUMN leaf_category_title SET NOT NULL,
  ALTER COLUMN leaf_category_slug SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS leaf_categories_leaf_category_slug_key
  ON leaf_categories(leaf_category_slug);

CREATE TABLE IF NOT EXISTS scrape_runs (
  id uuid PRIMARY KEY,
  keyword text NOT NULL,
  category_id uuid REFERENCES categories(uuid) ON UPDATE CASCADE,
  subcategory_id uuid REFERENCES subcategories(uuid) ON UPDATE CASCADE,
  leaf_category_id uuid REFERENCES leaf_categories(id) ON UPDATE CASCADE,
  status text NOT NULL DEFAULT 'running',
  started_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  error text
);

CREATE TABLE IF NOT EXISTS products (
  id uuid PRIMARY KEY,
  title text,
  product_url text UNIQUE,
  image_url text,
  company text,
  category_id uuid REFERENCES categories(uuid) ON UPDATE CASCADE,
  subcategory_id uuid REFERENCES subcategories(uuid) ON UPDATE CASCADE,
  leaf_category_id uuid REFERENCES leaf_categories(id) ON UPDATE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS product_scrape_results (
  id uuid PRIMARY KEY,
  scrape_run_id uuid NOT NULL REFERENCES scrape_runs(id) ON DELETE CASCADE,
  product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  page integer,
  position integer,
  price numeric(12, 2),
  price_usd numeric(12, 2),
  sold text,
  repeat_rate text,
  badges jsonb NOT NULL DEFAULT '[]'::jsonb,
  raw_text text,
  scrape_status text NOT NULL DEFAULT 'product_scraped',
  scraped_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (scrape_run_id, product_id)
);

CREATE TABLE IF NOT EXISTS product_details (
  id uuid PRIMARY KEY,
  product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  scrape_run_id uuid REFERENCES scrape_runs(id) ON DELETE SET NULL,
  success boolean NOT NULL DEFAULT false,
  scraped_at timestamptz,
  url text,
  product_title text,
  page_title text,
  supplier_name text,
  original_price numeric(12, 2),
  original_price_usd numeric(12, 2),
  promotional_price numeric(12, 2),
  promotional_price_usd numeric(12, 2),
  coupon_price numeric(12, 2),
  coupon_price_usd numeric(12, 2),
  first_piece_estimated_price numeric(12, 2),
  first_piece_estimated_price_usd numeric(12, 2),
  price_text text,
  quantity_price_tiers jsonb NOT NULL DEFAULT '[]'::jsonb,
  discount_rules jsonb NOT NULL DEFAULT '[]'::jsonb,
  pricing jsonb NOT NULL DEFAULT '{}'::jsonb,
  availability jsonb NOT NULL DEFAULT '{}'::jsonb,
  variants jsonb NOT NULL DEFAULT '{}'::jsonb,
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  media jsonb NOT NULL DEFAULT '{}'::jsonb,
  meta jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS product_detail_variants (
  uuid uuid PRIMARY KEY,
  product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  product_detail_id uuid NOT NULL REFERENCES product_details(id) ON DELETE CASCADE,
  variant_index integer NOT NULL,
  color_id uuid,
  color text,
  size_id uuid,
  size text,
  price numeric(12, 2),
  price_usd numeric(12, 2),
  image_url text,
  stock_quantity bigint,
  stock_text text,
  length_cm numeric(12, 2),
  width_cm numeric(12, 2),
  height_cm numeric(12, 2),
  volume_cm3 numeric(14, 3),
  weight_g numeric(12, 2),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (product_detail_id, variant_index)
);

ALTER TABLE product_scrape_results
  ADD COLUMN IF NOT EXISTS scrape_status text NOT NULL DEFAULT 'product_scraped';

ALTER TABLE product_scrape_results
  ADD COLUMN IF NOT EXISTS price_usd numeric(12, 2);

ALTER TABLE product_details
  ADD COLUMN IF NOT EXISTS original_price numeric(12, 2),
  ADD COLUMN IF NOT EXISTS original_price_usd numeric(12, 2),
  ADD COLUMN IF NOT EXISTS promotional_price numeric(12, 2),
  ADD COLUMN IF NOT EXISTS promotional_price_usd numeric(12, 2),
  ADD COLUMN IF NOT EXISTS coupon_price numeric(12, 2),
  ADD COLUMN IF NOT EXISTS coupon_price_usd numeric(12, 2),
  ADD COLUMN IF NOT EXISTS first_piece_estimated_price numeric(12, 2),
  ADD COLUMN IF NOT EXISTS first_piece_estimated_price_usd numeric(12, 2),
  ADD COLUMN IF NOT EXISTS price_text text,
  ADD COLUMN IF NOT EXISTS quantity_price_tiers jsonb NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS discount_rules jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE product_detail_variants
  ADD COLUMN IF NOT EXISTS price numeric(12, 2);

ALTER TABLE product_detail_variants
  ADD COLUMN IF NOT EXISTS price_usd numeric(12, 2);

ALTER TABLE product_detail_variants
  ADD COLUMN IF NOT EXISTS image_url text;

ALTER TABLE product_detail_variants
  ADD COLUMN IF NOT EXISTS stock_quantity bigint,
  ADD COLUMN IF NOT EXISTS stock_text text;

ALTER TABLE product_detail_variants
  ALTER COLUMN stock_quantity TYPE bigint USING stock_quantity::bigint;

ALTER TABLE product_details
  DROP COLUMN IF EXISTS description,
  DROP COLUMN IF EXISTS raw;

ALTER TABLE product_detail_variants
  DROP COLUMN IF EXISTS raw;

CREATE INDEX IF NOT EXISTS subcategories_category_id_idx ON subcategories(category_id);
CREATE INDEX IF NOT EXISTS leaf_categories_category_id_idx ON leaf_categories(category_id);
CREATE INDEX IF NOT EXISTS leaf_categories_subcategory_id_idx ON leaf_categories(subcategory_id);
CREATE INDEX IF NOT EXISTS scrape_runs_category_id_idx ON scrape_runs(category_id);
CREATE INDEX IF NOT EXISTS scrape_runs_subcategory_id_idx ON scrape_runs(subcategory_id);
CREATE INDEX IF NOT EXISTS products_category_id_idx ON products(category_id);
CREATE INDEX IF NOT EXISTS products_subcategory_id_idx ON products(subcategory_id);
CREATE INDEX IF NOT EXISTS products_leaf_category_id_idx ON products(leaf_category_id);
CREATE INDEX IF NOT EXISTS product_scrape_results_product_id_idx ON product_scrape_results(product_id);
CREATE INDEX IF NOT EXISTS product_scrape_results_latest_idx ON product_scrape_results(product_id, scraped_at DESC);
CREATE INDEX IF NOT EXISTS product_details_product_id_idx ON product_details(product_id);
CREATE INDEX IF NOT EXISTS product_details_latest_idx ON product_details(product_id, scraped_at DESC NULLS LAST, created_at DESC);
CREATE INDEX IF NOT EXISTS product_details_original_price_idx ON product_details(original_price);
CREATE INDEX IF NOT EXISTS product_details_promotional_price_idx ON product_details(promotional_price);
CREATE INDEX IF NOT EXISTS product_details_original_price_usd_idx ON product_details(original_price_usd);
CREATE INDEX IF NOT EXISTS product_details_promotional_price_usd_idx ON product_details(promotional_price_usd);
CREATE INDEX IF NOT EXISTS product_detail_variants_product_id_idx ON product_detail_variants(product_id);
CREATE INDEX IF NOT EXISTS product_detail_variants_detail_id_idx ON product_detail_variants(product_detail_id);
CREATE INDEX IF NOT EXISTS product_detail_variants_color_size_idx ON product_detail_variants(product_id, color_id, size_id);
