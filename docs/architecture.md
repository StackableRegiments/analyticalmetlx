---
layout: default
title: Architecture
---

## Content

- [Concepts](#concepts) 
- [Architecture](#architecture) 
- [Security](#security) 
- [Capabilities](#capabilities) 

## Concepts

MeTL is at its heart a message hub with all messages being persisted for later retrieval.
This makes it a system for virtualising rooms.

Messages are sent to a specific space, and only people who are in that room will hear the message.

Messages can be user level, and be visible to a human user, or system level and used to coordinate clients behaviour.

All messages which have ever been through a room are retained, and can be replayed in order.
Server side mechanisms optimize the results so that, for instance, a sentence which was published, moved and
then later deleted does not show up in the client history at all.

Conversations are structured as a collection of slides and some metadata.
This is similar to the structure of a PowerPoint presentation, which enables some interoperability.

A slide is a room.

Each user has a private room on each slide.

Each conversation has a conversation global room.
Quizzes, submissions and attachments use this space, as they are not specific to a slide.

A server global room carries configuration data to all connected clients
(when a conversation is shared differently, for instance, this is broadcast globally in case that
conversation needs to be added or removed from a search result).

## Architecture

## Security

## Capabilities

