drop table if exists bank_account;

-- Not supported in psql:
-- create type if not exists account_type as enum ('S', 'U');
drop type if exists account_type;
create type account_type as enum ('S', 'U');

create table if not exists bank_account
(
    id           int            not null primary key,
    balance      numeric(19, 2) not null,
    name         varchar(128)   not null,
    type         account_type   not null,
    updated_at   timestamptz    not null default clock_timestamp()
);

alter table bank_account
    add constraint check_account_positive_balance check (balance >= 0);

insert into bank_account (id, balance, name, type)
select i,
       100000.00,
       concat('system:', (i::varchar)),
       'S'
from generate_series(1, 10) as i;

insert into bank_account (id, balance, name, type)
select i,
       0.00,
       concat('user:', (i::varchar)),
       'U'
from generate_series(11, 1010) as i;
