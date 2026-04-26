-- Helper: get role of the current authenticated user
create or replace function current_user_role()
returns text as $$
  select role from profiles where user_id = auth.uid()
$$ language sql security definer stable;

-- PROFILES: authenticated users can read their own profile
create policy "user reads own profile" on profiles
  for select to authenticated
  using (user_id = auth.uid());

-- BOOKINGS: both roles can read and insert; only owner can update/delete
create policy "authenticated read bookings" on bookings
  for select to authenticated using (true);

create policy "owner insert bookings" on bookings
  for insert to authenticated
  with check (current_user_role() = 'owner');

create policy "owner update bookings" on bookings
  for update to authenticated
  using (current_user_role() = 'owner');

-- INQUIRIES: both roles can read and update status
create policy "authenticated read inquiries" on inquiries
  for select to authenticated using (true);

create policy "authenticated update inquiry status" on inquiries
  for update to authenticated
  using (true)
  with check (true);

-- BOOKING_LINE_ITEMS: owner only
create policy "owner all booking_line_items" on booking_line_items
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- BOOKING_PAYMENTS: owner only
create policy "owner all booking_payments" on booking_payments
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- BOOKING_EXPENSES: owner only
create policy "owner all booking_expenses" on booking_expenses
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- OVERHEAD_EXPENSES: owner only
create policy "owner all overhead_expenses" on overhead_expenses
  for all to authenticated
  using (current_user_role() = 'owner')
  with check (current_user_role() = 'owner');

-- ── Grants ───────────────────────────────────────────────────────────────────
-- RLS policies restrict what rows are visible, but grants control whether
-- the authenticated role can touch the table at all. Both are required.

grant usage on schema public to authenticated;
grant select on profiles to authenticated;
grant select, update on inquiries to authenticated;
grant select, insert, update on bookings to authenticated;
grant all on booking_line_items to authenticated;
grant all on booking_payments to authenticated;
grant all on booking_expenses to authenticated;
grant all on overhead_expenses to authenticated;
