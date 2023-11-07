-- drop table product cascade ;
create table if not exists product
(
    id              uuid           not null default gen_random_uuid(),
    version         int            not null,
    inventory       int            not null,
    name            varchar(128)   not null,
    price           numeric(19, 2) not null,
    sku             varchar(128)   not null unique,
    last_updated_at timestamptz    not null default clock_timestamp(),

    primary key (id, version)
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


-- ######### Update rewrite example

-- Singletons
UPDATE product SET inventory=11, price=200.00, version=1, last_updated_at = with_min_timestamp(transaction_timestamp()) WHERE id='00000000-0000-0000-0000-000000000000' and version=0;
UPDATE product SET inventory=21, price=300.00, version=1, last_updated_at = with_min_timestamp(transaction_timestamp()) WHERE id='00000000-0000-0000-0000-000000000001' and version=0;
-- Batch rewrite
update product set inventory=dt.inventory, price=dt.price, version=dt.version, last_updated_at=with_min_timestamp(dt.last_updated_at)
from (select unnest(ARRAY[11,21]) as inventory,
             unnest(ARRAY[200.00,300.00]) as price,
             transaction_timestamp() as last_updated_at,
             unnest(ARRAY['00000000-0000-0000-0000-000000000000'::uuid,'00000000-0000-0000-0000-000000000001'::uuid]) as id, -- predicate
             unnest(ARRAY[0,0]) as version) -- predicate
         as dt
where product.id=dt.id and product.version=dt.version;

-- ##########################################################

SELECT * FROM account WHERE id in (?, ?) FOR UPDATE;
-- ["192d4dbf-469b-40c8-be2c-d16e5451a71b","687781aa-36f8-460e-858b-d842651d21d2"]

UPDATE account
SET balance   = ?,
    updated_at=clock_timestamp()
WHERE id = ?
  AND closed = false
  AND currency=?
  AND (?) * abs(allow_negative - 1) >= 0;
-- ["99998.62","192d4dbf-469b-40c8-be2c-d16e5451a71b","USD","99998.62"],["100001.38","687781aa-36f8-460e-858b-d842651d21d2","USD","100001.38"]

INSERT INTO transaction (id,region,booking_date,transfer_date,transaction_type) VALUES(?, ?, ?, ?, ?);
-- ["a6ee7cc6-3cee-4d77-8ed0-2f0e682693e9","europe-west1","2023-11-03","2023-11-03","ABC"]]}

INSERT INTO transaction_item (region, transaction_id, account_id, amount, currency, note, running_balance) VALUES(?,?,?,?,?,?,?);
-- ["europe-west1","a6ee7cc6-3cee-4d77-8ed0-2f0e682693e9","192d4dbf-469b-40c8-be2c-d16e5451a71b","-1.38","USD","A cockroach can live for a week without its head. Due to their open circulatory system, and the fact that they breathe through little holes in each of their body segments, they are not dependent on the mouth or head to breathe. The roach only dies because without a mouth, it can't drink water and dies of thirst.","100000.00"],["europe-west1","a6ee7cc6-3cee-4d77-8ed0-2f0e682693e9","687781aa-36f8-460e-858b-d842651d21d2","1.38","USD","The world's largest cockroach (which lives in South America) is six inches long with a one-foot wingspan.","100000.00"]]}


select a1_0.id,
       a1_0.account_type,
       a1_0.allow_negative,
       a1_0.balance,
       a1_0.currency,
       a1_0.closed,
       a1_0.description,
       a1_0.inserted_at,
       a1_0.name,
       a1_0.region,
       a1_0.updated_at
from account a1_0
where a1_0.id in (?, ?) for share;
-- :[["5dc89a02-9ec2-4903-937c-3df52ef4bd53","506a0cf6-c690-4e3f-b816-4bcd665eb9dd"]]}
insert into transaction (booking_date,region,transaction_type,transfer_date,id) values (?,?,?,?,?);
-- [["2023-11-03","europe-west1","ABC","2023-11-03","b7960c05-7c3a-4a1f-9f15-9a2ffd79438b"]]}
insert into transaction_item (amount,currency,note,region,running_balance,account_id,transaction_id) values (?,?,?,?,?,?,?);
-- :[["6.28","USD","Roaches can live up to a week without their head.","europe-west1","100000.00","5dc89a02-9ec2-4903-937c-3df52ef4bd53","b7960c05-7c3a-4a1f-9f15-9a2ffd79438b"],["-6.28","USD","A one-day-old baby cockroach, which is about the size of a speck of dust, can run almost as fast as its parents.","europe-west1","100000.00","506a0cf6-c690-4e3f-b816-4bcd665eb9dd","b7960c05-7c3a-4a1f-9f15-9a2ffd79438b"]]}
update account
set allow_negative=?,
    balance=?,
    currency=?,
    closed=?,
    description=?,
    name=?,
    updated_at=?
where id=?;
-- :[["0","99993.72","USD","false","Non pro tractatos tritani in","user:7659","NULL(TIMESTAMP)","506a0cf6-c690-4e3f-b816-4bcd665eb9dd"],["0","100006.28","USD","false","Ancillae nibh mandamus moderatius iaculis esse facilisi petentium","user:3027","NULL(TIMESTAMP)","5dc89a02-9ec2-4903-937c-3df52ef4bd53"]]}
