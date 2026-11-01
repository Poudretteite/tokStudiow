WITH numeric_scale AS (
INSERT INTO grading_scales (name, type)
VALUES ('Skala 2,0–5,0', 'NUMERIC')
    RETURNING id
    )
INSERT INTO grade_values (grading_scale_id, label, numeric_value, is_passing, sort_order)
SELECT id, v.label, v.numeric_value, v.is_passing, v.sort_order
FROM numeric_scale,
     (VALUES ('2,0', 2.0, FALSE, 1),
             ('3,0', 3.0, TRUE,  2),
             ('3,5', 3.5, TRUE,  3),
             ('4,0', 4.0, TRUE,  4),
             ('4,5', 4.5, TRUE,  5),
             ('5,0', 5.0, TRUE,  6)) AS v(label, numeric_value, is_passing, sort_order);

WITH pass_fail_scale AS (
INSERT INTO grading_scales (name, type)
VALUES ('Zaliczenie', 'PASS_FAIL')
    RETURNING id
    )
INSERT INTO grade_values (grading_scale_id, label, numeric_value, is_passing, sort_order)
SELECT id, v.label, NULL, v.is_passing, v.sort_order
FROM pass_fail_scale,
     (VALUES ('zal',  TRUE,  1),
             ('nzal', FALSE, 2)) AS v(label, is_passing, sort_order);