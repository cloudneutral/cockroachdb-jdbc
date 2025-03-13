show create table product;

insert into product (id, version, inventory, name, price, sku)
select gen_random_uuid(),
       0,
       500 + (no::float * random())::int,
       concat('product-', no::varchar),
       500.0 + (no::float * random())::decimal,
       gen_random_uuid()::string
from generate_series(1, 100) no;

insert into product (id, version, inventory, name, price, sku)
select '00000000-0000-0000-0000-000000000000',
       0,
       10,
       'product-x',
       100.50,
       gen_random_uuid()::string;

insert into product (id, version, inventory, name, price, sku)
select '00000000-0000-0000-0000-000000000001',
       0,
       20,
       'product-y',
       200.50,
       gen_random_uuid()::string;

insert into product (id, inventory, name, price, sku)
select '00000000-0000-0000-0000-000000000001',
       20,
       'product-y',
       200.50,
       gen_random_uuid()::string
on conflict on constraint product_sku_key do nothing;

insert into product (id, version, inventory, name, price, sku)
select '00000000-0000-0000-0000-000000000001',
       0,
       20,
       'product-y',
       (select 1),
       gen_random_uuid()::string;


select *
from product
where id = '00000000-0000-0000-0000-000000000001'
  and version = 0;

select unnest(ARRAY ['00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000001']);

select unnest(ARRAY [11,21])                                                                         as new_inventory,
       unnest(ARRAY [200.00,300.00])                                                                 as new_price,
       unnest(ARRAY ['00000000-0000-0000-0000-000000000000','00000000-0000-0000-0000-000000000001']) as id,
       unnest(ARRAY [0,0])                                                                           as version;

-- Singletons
UPDATE product
SET inventory=11,
    price=200.00,
    version=1,
    last_updated_at = with_min_timestamp(transaction_timestamp())
WHERE id = '00000000-0000-0000-0000-000000000000'
  and version = 0;
UPDATE product
SET inventory=21,
    price=300.00,
    version=1,
    last_updated_at = with_min_timestamp(transaction_timestamp())
WHERE id = '00000000-0000-0000-0000-000000000001'
  and version = 0;

-- Batch rewrite
update product
set inventory=dt.inventory,
    price=dt.price,
    version=dt.version,
    last_updated_at=with_min_timestamp(dt.last_updated_at)
from (select unnest(ARRAY [11,21])                                                                                     as inventory,
             unnest(ARRAY [200.00,300.00])                                                                             as price,
             transaction_timestamp()                                                                                   as last_updated_at,
             unnest(ARRAY ['00000000-0000-0000-0000-000000000000'::uuid,'00000000-0000-0000-0000-000000000001'::uuid]) as id, -- predicate
             unnest(ARRAY [0,0])                                                                                       as version) -- predicate
         as dt
where product.id = dt.id
  and product.version = dt.version;



UPDATE product
SET inventory=?,
    price=?,
    version = version + 1,
    last_updated_at = with_min_timestamp(transaction_timestamp())
WHERE id=?
  and version = 0;

update product
set inventory       = _dt.p1,
    price           = _dt.p2,
    version         = product.version + 1,
    last_updated_at = with_min_timestamp(transaction_timestamp())
from (select unnest(ARRAY [11,21])                                                                                     as p1,
             unnest(ARRAY [200.00,300.00])                                                                             as p2,
             unnest(ARRAY ['00000000-0000-0000-0000-000000000000'::uuid,'00000000-0000-0000-0000-000000000001'::uuid]) as p3) as _dt
where product.id = _dt.p3
  and product.version = 0;