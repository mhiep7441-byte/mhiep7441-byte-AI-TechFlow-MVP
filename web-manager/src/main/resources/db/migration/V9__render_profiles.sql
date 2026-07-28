alter table campaigns add column if not exists audio_mode varchar(30) not null default 'narrated';
alter table campaigns add column if not exists video_provider varchar(30) not null default 'kenburns';
alter table campaigns add column if not exists aspect_ratio varchar(10) not null default '9:16';
alter table campaigns add column if not exists render_quality varchar(20) not null default 'draft';

alter table work_tasks add column if not exists audio_mode varchar(30) not null default 'narrated';
alter table work_tasks add column if not exists video_provider varchar(30) not null default 'kenburns';
alter table work_tasks add column if not exists aspect_ratio varchar(10) not null default '9:16';
alter table work_tasks add column if not exists render_quality varchar(20) not null default 'draft';
