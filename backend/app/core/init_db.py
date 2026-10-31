import asyncio
from app.core.database import engine, Base
import app.models  # noqa: F401

async def init_tables():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("Database tables initialized successfully.")

if __name__ == "__main__":
    asyncio.run(init_tables())

