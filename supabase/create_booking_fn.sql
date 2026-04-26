create or replace function create_booking_with_items(
  p_booking jsonb,
  p_line_items jsonb,
  p_payment jsonb
) returns uuid as $$
declare
  v_booking_id uuid;
begin
  insert into bookings (customer_name, phone, event_date, event_type, guest_count, addons, notes, status)
  values (
    p_booking->>'customer_name',
    p_booking->>'phone',
    (p_booking->>'event_date')::date,
    p_booking->>'event_type',
    (p_booking->>'guest_count')::int,
    array(select jsonb_array_elements_text(p_booking->'addons')),
    p_booking->>'notes',
    'confirmed'
  )
  returning id into v_booking_id;

  insert into booking_line_items (booking_id, label, amount)
  select v_booking_id, item->>'label', (item->>'amount')::numeric
  from jsonb_array_elements(p_line_items) as item;

  insert into booking_payments (booking_id, amount, date, note)
  values (
    v_booking_id,
    (p_payment->>'amount')::numeric,
    (p_payment->>'date')::date,
    p_payment->>'note'
  );

  return v_booking_id;
end;
$$ language plpgsql security definer;
