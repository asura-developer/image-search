create extension if not exists pg_trgm;
create extension if not exists unaccent;
create extension if not exists vector;
create extension if not exists pgcrypto;

create table if not exists product_embeddings (
    id uuid primary key default gen_random_uuid(),
    product_id uuid not null unique references products(id) on delete cascade,
    embedding vector(512) not null,
    source_image_url text,
    model_version text not null default 'clip-vit-base-patch32',
    created_at timestamptz not null default now()
);

create index if not exists product_embeddings_product_id_idx
    on product_embeddings(product_id);

create index if not exists product_embeddings_vector_idx
    on product_embeddings
    using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);

create table if not exists product_search_documents (
    product_id uuid primary key references products(id) on delete cascade,
    title text,
    product_url text,
    image_url text,
    company text,
    category_id uuid,
    category_title text,
    category_slug text,
    subcategory_title text,
    subcategory_slug text,
    leaf_category_title text,
    leaf_category_slug text,
    original_price numeric(12, 2),
    sortable_price numeric(12, 2),
    search_text text not null default '',
    search_vector tsvector not null default ''::tsvector,
    updated_at timestamptz not null default now()
);

create index if not exists product_search_documents_vector_idx
    on product_search_documents
    using gin (search_vector);

create index if not exists product_search_documents_price_idx
    on product_search_documents(sortable_price);

create index if not exists product_search_documents_updated_at_idx
    on product_search_documents(updated_at desc nulls last);

create index if not exists product_search_documents_category_slug_idx
    on product_search_documents((lower(category_slug)));

create index if not exists product_search_documents_subcategory_slug_idx
    on product_search_documents((lower(subcategory_slug)));

create index if not exists product_search_documents_leaf_category_slug_idx
    on product_search_documents((lower(leaf_category_slug)));

create index if not exists product_search_documents_title_trgm_idx
    on product_search_documents
    using gin ((lower(title)) gin_trgm_ops);

create index if not exists product_search_documents_company_trgm_idx
    on product_search_documents
    using gin ((lower(company)) gin_trgm_ops);

create index if not exists product_search_documents_category_trgm_idx
    on product_search_documents
    using gin ((lower(category_title)) gin_trgm_ops);

with latest_details as (
    select distinct on (pd.product_id)
        pd.product_id,
        pd.product_title,
        pd.supplier_name,
        pd.original_price,
        pd.original_price_usd,
        pd.promotional_price,
        pd.promotional_price_usd,
        pd.price_text,
        pd.scraped_at,
        pd.created_at
    from product_details pd
    order by pd.product_id, pd.scraped_at desc nulls last, pd.created_at desc
)
insert into product_search_documents (
    product_id,
    title,
    product_url,
    image_url,
    company,
    category_id,
    category_title,
    category_slug,
    subcategory_title,
    subcategory_slug,
    leaf_category_title,
    leaf_category_slug,
    original_price,
    sortable_price,
    search_text,
    search_vector,
    updated_at
)
select
    p.id,
    coalesce(nullif(p.title, ''), ld.product_title),
    p.product_url,
    p.image_url,
    coalesce(nullif(p.company, ''), ld.supplier_name),
    c.uuid,
    c.category_title,
    c.slug,
    sc.sub_category_title,
    sc.sub_category_slug,
    lc.leaf_category_title,
    coalesce(lc.leaf_category_slug, lc.slug),
    coalesce(ld.original_price, ld.promotional_price),
    coalesce(ld.promotional_price_usd, ld.original_price_usd, ld.promotional_price, ld.original_price),
    lower(
        unaccent(
            concat_ws(
                ' ',
                p.id::text,
                p.title,
                ld.product_title,
                p.company,
                ld.supplier_name,
                c.category_title,
                c.slug,
                sc.sub_category_title,
                sc.sub_category_slug,
                lc.leaf_category_title,
                lc.leaf_category_slug,
                ld.price_text
            )
        )
    ),
    setweight(to_tsvector('simple', unaccent(coalesce(p.title, ld.product_title, ''))), 'A') ||
    setweight(to_tsvector('simple', unaccent(coalesce(p.company, ld.supplier_name, ''))), 'B') ||
    setweight(to_tsvector('simple', unaccent(coalesce(c.category_title, ''))), 'B') ||
    setweight(to_tsvector('simple', unaccent(coalesce(sc.sub_category_title, ''))), 'C') ||
    setweight(to_tsvector('simple', unaccent(coalesce(lc.leaf_category_title, ''))), 'C'),
    greatest(
        coalesce(p.updated_at, p.created_at, '-infinity'::timestamptz),
        coalesce(ld.scraped_at, ld.created_at, '-infinity'::timestamptz)
    )
from products p
left join latest_details ld on ld.product_id = p.id
left join categories c on c.uuid = p.category_id
left join subcategories sc on sc.uuid = p.subcategory_id
left join leaf_categories lc on lc.id = p.leaf_category_id
on conflict (product_id) do update set
    title = excluded.title,
    product_url = excluded.product_url,
    image_url = excluded.image_url,
    company = excluded.company,
    category_id = excluded.category_id,
    category_title = excluded.category_title,
    category_slug = excluded.category_slug,
    subcategory_title = excluded.subcategory_title,
    subcategory_slug = excluded.subcategory_slug,
    leaf_category_title = excluded.leaf_category_title,
    leaf_category_slug = excluded.leaf_category_slug,
    original_price = excluded.original_price,
    sortable_price = excluded.sortable_price,
    search_text = excluded.search_text,
    search_vector = excluded.search_vector,
    updated_at = excluded.updated_at;
