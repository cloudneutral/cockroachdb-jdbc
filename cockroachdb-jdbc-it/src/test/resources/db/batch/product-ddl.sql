create table if not exists product
(
    id        uuid           not null default gen_random_uuid(),
    inventory int            not null,
    name      varchar(128)   not null,
    price     numeric(19, 2) not null,
    sku       varchar(128)   not null unique,
    primary key (id)
);


