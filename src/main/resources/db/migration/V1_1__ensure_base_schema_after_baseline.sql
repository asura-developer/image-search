create table if not exists categories (
    category_title text not null,
    slug text not null unique,
    icon_url text,
    uuid uuid primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_name = 'categories' and column_name = 'id'
  ) and not exists (
    select 1 from information_schema.columns
    where table_name = 'categories' and column_name = 'uuid'
  ) then
    alter table categories rename column id to uuid;
  end if;
end $$;

alter table categories
    add column if not exists category_title text,
    add column if not exists icon_url text,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

update categories
set category_title = initcap(replace(slug, '-', ' '))
where category_title is null;

alter table categories
    alter column category_title set not null;

create table if not exists subcategories (
    uuid uuid primary key,
    sub_category_title text not null,
    sub_category_slug text not null unique,
    category_id uuid not null references categories(uuid) on update cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_name = 'subcategories' and column_name = 'id'
  ) and not exists (
    select 1 from information_schema.columns
    where table_name = 'subcategories' and column_name = 'uuid'
  ) then
    alter table subcategories rename column id to uuid;
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_name = 'subcategories' and column_name = 'slug'
  ) and not exists (
    select 1 from information_schema.columns
    where table_name = 'subcategories' and column_name = 'sub_category_slug'
  ) then
    alter table subcategories rename column slug to sub_category_slug;
  end if;
end $$;

alter table subcategories
    add column if not exists sub_category_title text,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

update subcategories
set sub_category_title = initcap(replace(sub_category_slug, '-', ' '))
where sub_category_title is null;

alter table subcategories
    alter column sub_category_title set not null;

create table if not exists leaf_categories (
    id uuid primary key,
    category_id uuid not null references categories(uuid) on update cascade,
    subcategory_id uuid not null references subcategories(uuid) on update cascade,
    leaf_category_title text,
    leaf_category_slug text,
    slug text not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table leaf_categories
    add column if not exists leaf_category_title text,
    add column if not exists leaf_category_slug text,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

update leaf_categories
set leaf_category_slug = slug
where leaf_category_slug is null;

update leaf_categories
set leaf_category_title = initcap(replace(leaf_category_slug, '-', ' '))
where leaf_category_title is null;

alter table leaf_categories
    alter column leaf_category_title set not null,
    alter column leaf_category_slug set not null;

create unique index if not exists leaf_categories_leaf_category_slug_key
    on leaf_categories(leaf_category_slug);

create table if not exists scrape_runs (
    id uuid primary key,
    keyword text not null,
    category_id uuid references categories(uuid) on update cascade,
    subcategory_id uuid references subcategories(uuid) on update cascade,
    leaf_category_id uuid references leaf_categories(id) on update cascade,
    status text not null default 'running',
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    error text
);

create table if not exists products (
    id uuid primary key,
    title text,
    product_url text unique,
    image_url text,
    company text,
    category_id uuid references categories(uuid) on update cascade,
    subcategory_id uuid references subcategories(uuid) on update cascade,
    leaf_category_id uuid references leaf_categories(id) on update cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists product_scrape_results (
    id uuid primary key,
    scrape_run_id uuid not null references scrape_runs(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    page integer,
    position integer,
    price numeric(12, 2),
    price_usd numeric(12, 2),
    sold text,
    repeat_rate text,
    badges jsonb not null default '[]'::jsonb,
    raw_text text,
    scrape_status text not null default 'product_scraped',
    scraped_at timestamptz not null default now(),
    unique (scrape_run_id, product_id)
);

create table if not exists product_details (
    id uuid primary key,
    product_id uuid not null references products(id) on delete cascade,
    scrape_run_id uuid references scrape_runs(id) on delete set null,
    success boolean not null default false,
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
    quantity_price_tiers jsonb not null default '[]'::jsonb,
    discount_rules jsonb not null default '[]'::jsonb,
    pricing jsonb not null default '{}'::jsonb,
    availability jsonb not null default '{}'::jsonb,
    variants jsonb not null default '{}'::jsonb,
    attributes jsonb not null default '{}'::jsonb,
    media jsonb not null default '{}'::jsonb,
    meta jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create table if not exists product_detail_variants (
    uuid uuid primary key,
    product_id uuid not null references products(id) on delete cascade,
    product_detail_id uuid not null references product_details(id) on delete cascade,
    variant_index integer not null,
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
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (product_detail_id, variant_index)
);

alter table product_scrape_results
    add column if not exists scrape_status text not null default 'product_scraped',
    add column if not exists price_usd numeric(12, 2);

alter table product_details
    add column if not exists original_price numeric(12, 2),
    add column if not exists original_price_usd numeric(12, 2),
    add column if not exists promotional_price numeric(12, 2),
    add column if not exists promotional_price_usd numeric(12, 2),
    add column if not exists coupon_price numeric(12, 2),
    add column if not exists coupon_price_usd numeric(12, 2),
    add column if not exists first_piece_estimated_price numeric(12, 2),
    add column if not exists first_piece_estimated_price_usd numeric(12, 2),
    add column if not exists price_text text,
    add column if not exists quantity_price_tiers jsonb not null default '[]'::jsonb,
    add column if not exists discount_rules jsonb not null default '[]'::jsonb;

alter table product_detail_variants
    add column if not exists price numeric(12, 2),
    add column if not exists price_usd numeric(12, 2),
    add column if not exists image_url text,
    add column if not exists stock_quantity bigint,
    add column if not exists stock_text text;

alter table product_detail_variants
    alter column stock_quantity type bigint using stock_quantity::bigint;

create index if not exists subcategories_category_id_idx on subcategories(category_id);
create index if not exists leaf_categories_category_id_idx on leaf_categories(category_id);
create index if not exists leaf_categories_subcategory_id_idx on leaf_categories(subcategory_id);
create index if not exists scrape_runs_category_id_idx on scrape_runs(category_id);
create index if not exists scrape_runs_subcategory_id_idx on scrape_runs(subcategory_id);
create index if not exists products_category_id_idx on products(category_id);
create index if not exists products_subcategory_id_idx on products(subcategory_id);
create index if not exists products_leaf_category_id_idx on products(leaf_category_id);
create index if not exists product_scrape_results_product_id_idx on product_scrape_results(product_id);
create index if not exists product_scrape_results_latest_idx on product_scrape_results(product_id, scraped_at desc);
create index if not exists product_details_product_id_idx on product_details(product_id);
create index if not exists product_details_latest_idx on product_details(product_id, scraped_at desc nulls last, created_at desc);
create index if not exists product_details_original_price_idx on product_details(original_price);
create index if not exists product_details_promotional_price_idx on product_details(promotional_price);
create index if not exists product_details_original_price_usd_idx on product_details(original_price_usd);
create index if not exists product_details_promotional_price_usd_idx on product_details(promotional_price_usd);
create index if not exists product_detail_variants_product_id_idx on product_detail_variants(product_id);
create index if not exists product_detail_variants_detail_id_idx on product_detail_variants(product_detail_id);
create index if not exists product_detail_variants_color_size_idx on product_detail_variants(product_id, color_id, size_id);
