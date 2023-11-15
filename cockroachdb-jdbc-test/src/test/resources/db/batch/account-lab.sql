create table user_account
(
    id             uuid           not null default gen_random_uuid(),
    city           string         not null,
    balance        decimal(19, 2) not null,
    currency       string         not null default 'USD',
    balance_money  string as (concat(balance::string, ' ', currency)) virtual,
    name           varchar(128)   not null,
    description    varchar(256)   null,
    type           varchar(32)    not null,
    closed         boolean        not null default false,
    allow_negative integer        not null default 0,
    updated_at     timestamptz    not null default clock_timestamp(),

    primary key (id)
);

INSERT INTO user_account (id, city, balance, currency, name, type, allow_negative)
VALUES ('00000000-0000-0000-0000-000000000000', 'stockholm', '100.00', 'SEK', 'test:1', 'A', 1),
       ('00000000-0000-0000-0000-000000000001', 'stockholm', '100.00', 'SEK', 'test:2', 'A', 1),
       ('00000000-0000-0000-0000-000000000002', 'stockholm', '100.00', 'SEK', 'test:3', 'L', 0),
       ('00000000-0000-0000-0000-000000000003', 'stockholm', '100.00', 'SEK', 'test:4', 'L', 0)
;

select * from user_account;

UPDATE user_account SET balance = balance + ?, updated_at = clock_timestamp()
WHERE id = ?
  AND closed = false
  AND (balance + ?) * abs(allow_negative - 1) >= 0;

UPDATE user_account SET balance = balance + -150, updated_at = clock_timestamp()
WHERE id = '00000000-0000-0000-0000-000000000000'::UUID
  AND closed = false
  AND (balance + -150) * abs(allow_negative - 1) >= 0;

UPDATE user_account SET balance = balance + -150, updated_at = clock_timestamp()
WHERE id = '00000000-0000-0000-0000-000000000002'::UUID
  AND closed = false
  AND (balance + -150) * abs(allow_negative - 1) >= 0;

UPDATE user_account SET balance = user_account.balance + _dt.balance, updated_at=clock_timestamp()
FROM (SELECT
          unnest(ARRAY [-150, -150, -150, -150])   as balance,
          unnest(ARRAY ['00000000-0000-0000-0000-000000000000'::UUID,
                        '00000000-0000-0000-0000-000000000001'::UUID,
                        '00000000-0000-0000-0000-000000000002'::UUID,
                        '00000000-0000-0000-0000-000000000003'::UUID]) as id
      )
    as _dt
WHERE user_account.id = _dt.id
  AND user_account.closed = false
  AND (user_account.balance + _dt.balance) * abs(user_account.allow_negative - 1) >= 0;

