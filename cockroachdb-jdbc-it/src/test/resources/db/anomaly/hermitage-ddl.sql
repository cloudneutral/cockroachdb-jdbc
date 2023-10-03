create table if not exists test
(
    id    int,
    value int
);

delete from test where 1 = 1;

insert into test (id, value)
values (1, 10),
       (2, 20);

