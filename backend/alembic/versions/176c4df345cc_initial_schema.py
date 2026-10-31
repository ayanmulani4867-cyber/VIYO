"""initial_schema

Revision ID: 176c4df345cc
Revises: 
Create Date: 2026-10-31 15:16:23.426800

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa
from app.core.database import Base
import app.models  # Ensure all models are registered in Base.metadata

# revision identifiers, used by Alembic.
revision: str = '176c4df345cc'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    bind = op.get_bind()
    Base.metadata.create_all(bind=bind)


def downgrade() -> None:
    bind = op.get_bind()
    Base.metadata.drop_all(bind=bind)
