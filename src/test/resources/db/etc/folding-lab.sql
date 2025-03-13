-- Schema

create table if not exists account
(
    id      uuid           not null default gen_random_uuid(),
    balance decimal(19, 2) not null,
    name    varchar(128)   not null,

    primary key (id)
);

create table if not exists transfer
(
    id           uuid not null default gen_random_uuid(),
    booking_date date not null default current_date(),

    primary key (id)
);

create table if not exists transfer_item
(
    transfer_id     uuid           not null,
    account_id      uuid           not null,
    amount          decimal(19, 2) not null,
    running_balance decimal(19, 2) not null,

    primary key (transfer_id, account_id)
);

alter table if exists transfer_item
    add constraint if not exists fk_region_ref_transfer
    foreign key (transfer_id) references transfer (id);

alter table if exists transfer_item
    add constraint if not exists fk_region_ref_account
    foreign key (account_id) references account (id);

-- Data

upsert
into account (id, balance, name)
select '10000000-0000-0000-0000-000000000000',
       500.00,
       md5(random()::text);

upsert
into account (id, balance, name)
select '20000000-0000-0000-0000-000000000000',
       500.00,
       md5(random()::text);

--
-- Business txn
--

begin;
-- gen_random_uuid()
insert into transfer (id) values ( '00000000-0000-0000-0000-000000000000') returning id,booking_date;
insert into transfer_item (transfer_id, account_id, amount, running_balance)
    values ('00000000-0000-0000-0000-000000000000', '10000000-0000-0000-0000-000000000000', 75.00,
            (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'));
insert into transfer_item (transfer_id, account_id, amount, running_balance)
    values ('00000000-0000-0000-0000-000000000000', '20000000-0000-0000-0000-000000000000', -75.00,
            (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'));
update account set balance = balance + 75.00 where id = '10000000-0000-0000-0000-000000000000';
update account set balance = balance - 75.00 where id = '20000000-0000-0000-0000-000000000000';
commit;

-- select * from account;
-- select * from transfer;
-- select * from transfer_item;

-- truncate table transfer_item cascade ;
-- truncate table transfer cascade ;

--
-- Modifying CTE
--

with head as (
    insert into transfer (id) values (gen_random_uuid())
        returning id,booking_date),
     item1 as (
         insert into transfer_item (transfer_id, account_id, amount, running_balance)
             values ((select id from head),
                     '10000000-0000-0000-0000-000000000000',
                     75.00,
                     (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'))
             returning transfer_id),
     item2 as (
         insert into transfer_item (transfer_id, account_id, amount, running_balance)
             values ((select id from head),
                     '20000000-0000-0000-0000-000000000000',
                     -75.00,
                     (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'))
             returning transfer_id)
update account
set balance=account.balance + dt.balance
from (select unnest(array [75, -75])                                                                                   as balance,
             unnest(array ['10000000-0000-0000-0000-000000000000'::uuid,'20000000-0000-0000-0000-000000000000'::uuid]) as id) as dt
where account.id = dt.id
returning account.id, account.balance;