create table if not exists account
(
    name    varchar(128)   not null,
    type    varchar(25)    not null,
    balance numeric(19, 2) not null,

    primary key (name, type)
);

delete from account where 1 = 1;

insert into account
values ('alice', 'asset', 500),
       ('alice', 'expense', 500),
       ('bob', 'asset', 500),
       ('bob', 'expense', 500),
       ('carol', 'asset', 500),
       ('carol', 'expense', 500),
       ('emelie', 'asset', 500),
       ('emelie', 'expense', 500);

insert into account (name,type,balance)
select concat('user-',no::varchar),
       'asset',
       500+(no::FLOAT * random())::decimal
from generate_series(1, 5000) no;

insert into account (name,type,balance)
select concat('user-',no::varchar),
       'expense',
       500+(no::FLOAT * random())::decimal
from generate_series(1, 5000) no;

