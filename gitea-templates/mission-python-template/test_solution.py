import pytest

from solution import add


def test_add_two_positive_numbers():
    assert add(2, 3) == 5


def test_add_two_negative_numbers():
    assert add(-5, -2) == -7


def test_add_zero_to_a_number():
    assert add(0, 7) == 7


def test_add_floating_point_numbers():
    assert add(0.1, 0.2) == pytest.approx(0.3)
