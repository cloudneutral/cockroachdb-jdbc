create table if not exists product
(
    id        uuid           not null default gen_random_uuid(),
    version   int            not null,
    inventory int            not null,
    name      varchar(128)   not null,
    price     numeric(19, 2) not null,
    sku       varchar(128)   not null unique,
    primary key (id,version)
);
show create table product;

insert into product (id,version,inventory,name,price,sku)
select gen_random_uuid(),
       0,
       500+(no::float * random())::int,
       concat('product-',no::varchar),
       500.0+(no::float * random())::decimal,
       gen_random_uuid()::string
from generate_series(1, 100) no;

insert into product (id,version,inventory,name,price,sku)
select '00000000-0000-0000-0000-000000000000',
       0,
       10,
       'product-x',
       100.50,
       gen_random_uuid()::string;

insert into product (id,version,inventory,name,price,sku)
select '00000000-0000-0000-0000-000000000001',
       0,
       20,
       'product-y',
       200.50,
       gen_random_uuid()::string;


select * from product where id='00000000-0000-0000-0000-000000000000' ;
select * from product where id='00000000-0000-0000-0000-000000000001' and version=0;
select unnest(ARRAY['00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000001']);
select unnest(ARRAY[11,21]) as new_inventory,
       unnest(ARRAY[200.00,300.00]) as new_price,
       unnest(ARRAY['00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000001']) as id,
       unnest(ARRAY[0,0]) as version;


-- Singletons
UPDATE product SET inventory=11, price=200.00, version=version+1 WHERE id='00000000-0000-0000-0000-000000000000' and version=0;
UPDATE product SET inventory=21, price=300.00, version=version+1 WHERE id='00000000-0000-0000-0000-000000000001' and version=0;

-- Batch rewrite
update product set inventory=dt.new_inventory, price=dt.new_price, version=product.version+dt.version
from (select unnest(ARRAY[11,21]) as new_inventory,
             unnest(ARRAY[200.00,300.00]) as new_price,
             unnest(ARRAY['00000000-0000-0000-0000-000000000000'::uuid,'00000000-0000-0000-0000-000000000001'::uuid]) as id,
             unnest(ARRAY[0,0]) as version)
         as dt
where product.id=dt.id and product.version=dt.version;
