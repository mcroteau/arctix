create table bebops (
	id bigint PRIMARY KEY AUTO_INCREMENT,
	hoyahs varchar (254) NOT NULL
);

insert into bebops (hoyahs) values ('drumroll');
insert into bebops (hoyahs) values ('pour favor');

create table todos (
	id bigint PRIMARY KEY AUTO_INCREMENT,
	title character varying(254) NOT NULL,
	complete boolean default false
);

create table todo_people (
	id bigint PRIMARY KEY AUTO_INCREMENT,
	todo_id bigint REFERENCES todos(id),
	person character varying (250)
);