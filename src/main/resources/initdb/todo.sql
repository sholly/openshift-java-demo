create table if not exists todoitems(
    id SERIAL,
    name VARCHAR(40),
    description text,
    finished boolean
    );

insert into todoitems(id, name, description, finished) values (1, 'buy milk', 'I need to buy milk', false);
insert into todoitems(id, name, description, finished) values (2, 'paint house', 'I need to paint the house', false);
insert into todoitems(id, name, description, finished) values (3, 'bathe dogs', 'The dogs need baths!', false);