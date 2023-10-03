create table if not exists doctors
(
    name     varchar(128) not null,
    on_call  boolean      not null,
    shift_id int          not null,

    primary key (name)
);

delete from doctors where 1=1;

insert into doctors
values ('alice', true, 1234),
       ('bob', true, 1234),
       ('carol', false, 1234);
