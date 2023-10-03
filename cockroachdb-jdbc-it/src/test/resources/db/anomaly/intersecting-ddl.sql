-- drop table classroom;
create table if not exists classroom
(
    class_id int not null,
    value    int not null
);

delete from classroom where 1 = 1;

insert into classroom (class_id, value)
values (1, 10),
       (1, 20),
       (2, 30),
       (2, 40);

