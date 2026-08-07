import { ActionIcon, Tooltip } from '@mantine/core';
import { IconRobot } from '@tabler/icons-react';
import { AiChatDrawer } from './AiChatDrawer';
import { useState } from 'react';

export function AiFloatingButton() {
  const [opened, setOpened] = useState(false);
  return (
    <>
      {!opened && (
        <Tooltip label="AI Copilot" position="left">
          <ActionIcon
            size="xl"
            radius="xl"
            variant="filled"
            color="blue"
            onClick={() => setOpened(true)}
            style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1000, width: 56, height: 56 }}
          >
            <IconRobot size={24} />
          </ActionIcon>
        </Tooltip>
      )}
      <AiChatDrawer opened={opened} onClose={() => setOpened(false)} />
    </>
  );
}
