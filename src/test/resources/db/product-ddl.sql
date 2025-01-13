-- drop table product cascade;

create table if not exists product
(
    id              uuid           not null default gen_random_uuid(),
    version         int            not null,
    inventory       int            not null default 0,
    name            varchar(128)   not null,
    description     varchar(256)   null,
    price           numeric(19, 2) not null,
    sku             varchar(128)   not null,
    last_updated_at timestamptz    not null default clock_timestamp(),

    primary key (id, version)
);

alter table product
    add constraint if not exists check_inventory check (inventory > 0);

create unique index if not exists product_sku_key on product (sku);



