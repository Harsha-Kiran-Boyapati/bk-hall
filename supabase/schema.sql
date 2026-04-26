-- BK Function Hall — Supabase Schema
-- Run this in Supabase SQL Editor to set up the database from scratch.

-- ============================================================
-- Tables
-- ============================================================

create table bookings (
  id uuid primary key default gen_random_uuid(),
  customer_name text not null,
  phone text not null,
  event_date date not null,
  event_type text not null,
  guest_count int,
  addons text[],
  notes text,
  status text default 'confirmed',
  created_at timestamptz default now()
);

create table inquiries (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text not null,
  event_date date not null,
  event_type text not null,
  guest_count int,
  addons text[],
  message text,
  status text default 'new',
  created_at timestamptz default now()
);

create table profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users not null,
  role text not null check (role in ('owner', 'staff')),
  name text not null
);

create table booking_line_items (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid references bookings not null,
  label text not null,
  amount numeric not null
);

create table booking_payments (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid references bookings not null,
  amount numeric not null,
  date date not null,
  note text
);

create table booking_expenses (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid references bookings not null,
  label text not null,
  amount numeric not null,
  date date not null,
  category text not null check (category in ('electricity','labour','catering','decoration','other'))
);

create table overhead_expenses (
  id uuid primary key default gen_random_uuid(),
  label text not null,
  amount numeric not null,
  month int not null,
  year int not null,
  category text not null check (category in ('electricity','maintenance','cleaning','labour','other'))
);

-- ============================================================
-- Public view — exposes only booked dates, hides customer details
-- ============================================================

create view public_booked_dates as
  select event_date from bookings where status = 'confirmed';

grant select on public_booked_dates to anon;

-- ============================================================
-- Row Level Security
-- ============================================================

alter table bookings enable row level security;
alter table inquiries enable row level security;
alter table profiles enable row level security;
alter table booking_line_items enable row level security;
alter table booking_payments enable row level security;
alter table booking_expenses enable row level security;
alter table overhead_expenses enable row level security;

-- Anon can submit inquiries, cannot read them back
create policy "anon insert inquiries" on inquiries
  for insert to anon
  with check (true);

-- ============================================================
-- Rate limiting — one inquiry per phone number per 10 minutes
-- ============================================================

create or replace function check_inquiry_rate_limit()
returns trigger as $$
begin
  if exists (
    select 1 from inquiries
    where phone = new.phone
    and created_at > now() - interval '10 minutes'
  ) then
    raise exception 'Too many submissions. Please wait before trying again.';
  end if;
  return new;
end;
$$ language plpgsql security definer;

create trigger inquiry_rate_limit
  before insert on inquiries
  for each row execute function check_inquiry_rate_limit();
